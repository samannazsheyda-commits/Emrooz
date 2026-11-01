#!/usr/bin/env python3
"""CPU smoke comparison of pinned FP32 and INT8 punctuation graphs.

Not a Persian quality/F1 evaluation: this compares model predictions on a small
explicit corpus, source-preserving rendering and CPU latency. Supply --corpus
UTF-8 JSONL {"text": ...} for a larger, representative corpus. Android latency
and expert-reviewed Persian gold labels require separate device/corpus testing.
"""
import argparse
import hashlib
import json
import platform
import re
import statistics
import time
import unicodedata
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import sentencepiece as spm

LABELS = ["", ".", ",", "?", "？", "，", "。", "、", "・", "।", "؟", "،", ";", "።", "፣", "፧"]
TERMINATORS = ".,!?;:؟،؛。？！、，።፣፧।…"
EXAMPLES = [
    "سلام خوبی امروز کجا میروی", "آیا فردا جلسه داریم", "میثم امروز می‌آید",
    "میثم گفت فردا ساعت ۹ جلسه داریم آیا تو هم می‌آیی", "من نمی‌روم چون حالم خوب نیست",
    "من هرگز نگفتم که میثم نیاید", "او گفت من این تصمیم را قبول ندارم",
    "من ۱۲۳ تومان ندارم", "قیمت این کالا ۱۲۵۰۰۰ تومان است آیا تخفیف دارد",
    "شماره سفارش من 12345 است لطفا بررسی کنید", "در سال ۱۴۰۵ فروش ۲۵ درصد افزایش یافت",
    "«میثم» گفت نمی‌آیم", 'او گفت "من نمی‌دانم" بعد رفت',
    "  میثم\tامروز نمی‌آید\nفردا می‌آید  ",
    "میثم گفت جلسه ساعت ۱۰ شروع می‌شود و ساعت ۱۲ تمام می‌شود",
    "آیا این درست است که او هیچ پولی دریافت نکرده", "چرا به من نگفتی که امروز تعطیل است",
    "چه کسی این کتاب را نوشته است", "امروز هوا خوب است اما فردا باران می‌بارد",
    "من تهران زندگی می‌کنم و میثم در شیراز زندگی می‌کند",
    "وزارت آموزش و پرورش اعلام کرد مدارس فردا تعطیل نیستند",
    "رئیس جمهور در نشست خبری گفت مذاکرات ادامه خواهد داشت",
    "لطفا نام میثم را تغییر نده", "این خبر را تأیید نمی‌کنم",
    "hello friend how are you today", "i do not want to cancel order 12345",
    "meysam said i will not come tomorrow", "the meeting is at 9 am can you join",
    "we paid 12.50 dollars yesterday", 'he said "i disagree" and left',
    "سلام؟ میثم گفت: نمی‌آیم.", "پروژه Emrooz امروز آماده می‌شود آیا نسخه Android کار می‌کند",
    "\n".join(["میثم امروز جلسه ندارد"] * 80) + "\nپایان متن",
    "ک" * 150 + "\tمیثم امروز می‌آید",
    "مِیثَم و محمدرضا گفتند زهرا و علیرضا فردا نمی‌آیند",
    "آیا میثم گفت که من هیچ وقت این کار را نکرده‌ام",
    "من نه پولی گرفتم نه قراردادی امضا کردم",
    "شماره پیگیری 009812345 و مبلغ ۱۲۵٫۵۰ تومان را تغییر نده",
    "\n  مِیثَم\tنمی‌آید 😄  Emrooz\r\n۱۲۵٫۵۰  ",
    "", "   \n\t", "؟ ! …",
]

def clean(word):
    return "".join(c for c in word if unicodedata.category(c)[0] not in "PS")

def lexical(text):
    return re.sub(r"\s+", " ", "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in text)).strip()

def plan(text, sp):
    chunks, chunk, count = [], [], 0
    for match in re.finditer(r"\S+", text):
        source = match.group()
        lexical_ends = [i + 1 for i, c in enumerate(source) if unicodedata.category(c)[0] in "LNM" or c in "\u200c\u200d"]
        if not lexical_ends:
            continue
        end = lexical_ends[-1]
        # sentencepiece4j encodeNormalized: NFKC + lower + whitespace normalization.
        normalized = unicodedata.normalize("NFKC", clean(source)).lower()
        ids = sp.encode(normalized)
        if not ids:
            continue
        if count + len(ids) > 126:
            if chunk:
                chunks.append(chunk)
            chunk, count = [], 0
        if len(ids) > 126:
            continue
        chunk.append((ids, match.start() + end, any(c in TERMINATORS for c in source[end:])))
        count += len(ids)
    if chunk:
        chunks.append(chunk)
    return chunks

def infer(session, chunks, text, sp):
    labels, insertions, all_outputs, durations = [], [], {}, []
    for chunk in chunks:
        flat = [token for ids, _, _ in chunk for token in ids]
        ids = [sp.bos_id()] + flat + [sp.eos_id()]
        started = time.perf_counter()
        result = session.run(None, {"input_ids": np.array([ids], dtype=np.int64)})
        durations.append((time.perf_counter() - started) * 1000)
        named = dict(zip([o.name for o in session.get_outputs()], result))
        for name, value in named.items():
            all_outputs.setdefault(name, []).extend(value[0, 1:-1].reshape(-1).tolist())
        predictions = named["post_preds"][0]
        labels.extend(predictions[1:-1].tolist())
        i = 0
        for word_ids, offset, has_punctuation in chunk:
            i += len(word_ids)
            if not has_punctuation and predictions[i] > 0:
                insertions.append((offset, LABELS[predictions[i]]))
    rendered = text
    for offset, punctuation in sorted(insertions, reverse=True):
        rendered = rendered[:offset] + punctuation + rendered[offset:]
    # Reconstruct source slices independently. This checks every character,
    # including original punctuation, whitespace, ZWNJ and unknown tokens.
    cursor, pieces = 0, []
    for offset, punctuation in sorted(insertions):
        pieces.extend((text[cursor:offset], punctuation))
        cursor = offset
    pieces.append(text[cursor:])
    assert rendered == "".join(pieces), "renderer changed source characters"
    assert re.findall(r"\s", text) == re.findall(r"\s", rendered), "renderer changed whitespace"
    assert text.count("\u200c") == rendered.count("\u200c"), "renderer changed ZWNJ"
    assert lexical(text) == lexical(rendered), "renderer changed lexical content"
    return labels, rendered, all_outputs, durations

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("quantized", type=Path)
    parser.add_argument("tokenizer", type=Path)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    corpus = [json.loads(line)["text"] for line in args.corpus.read_text().splitlines() if line.strip()] if args.corpus else EXAMPLES
    options = ort.SessionOptions()
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    sessions = [ort.InferenceSession(str(p), sess_options=options, providers=["CPUExecutionProvider"]) for p in (args.original, args.quantized)]
    contracts = [{"inputs": [{"name": v.name, "type": v.type, "shape": v.shape} for v in session.get_inputs()],
                  "outputs": [{"name": v.name, "type": v.type, "shape": v.shape} for v in session.get_outputs()]}
                 for session in sessions]
    assert contracts[0] == contracts[1], "quantization changed the I/O contract"
    assert [v.name for v in sessions[0].get_inputs()] == ["input_ids"]
    assert "post_preds" in [v.name for v in sessions[0].get_outputs()]
    sp = spm.SentencePieceProcessor(model_file=str(args.tokenizer))
    for session in sessions:
        session.run(None, {"input_ids": np.array([[sp.bos_id()] + sp.encode("سلام") + [sp.eos_id()]], dtype=np.int64)})
    rows, agreements, durations = [], {}, [[], []]
    punctuation_tp = punctuation_fp = punctuation_fn = 0
    for text in corpus:
        chunks = plan(text, sp)
        values = [infer(session, chunks, text, sp) for session in sessions]
        base, quantized = values
        for name in base[2]:
            same, total = agreements.get(name, (0, 0))
            aa, bb = base[2][name], quantized[2][name]
            agreements[name] = (same + sum(a == b for a, b in zip(aa, bb)), total + len(aa))
        for a, b in zip(base[0], quantized[0]):
            punctuation_tp += int(a == b and a > 0)
            punctuation_fp += int(b > 0 and a != b)
            punctuation_fn += int(a > 0 and a != b)
        for i in range(2):
            durations[i].extend(values[i][3])
        rows.append({"input": text, "original": base[1], "quantized": quantized[1], "content_post_agreement": sum(a == b for a, b in zip(base[0], quantized[0])), "content_tokens": len(base[0]), "chunks": len(chunks), "exact_source_characters_preserved": True, "same_rendered_output": base[1] == quantized[1]})
    report = {
        "scope": "smoke comparison, not gold-label Persian quality evaluation or Android benchmark",
        "cpu": platform.processor() or platform.machine(),
        "io_contract": contracts[0],
        "python": platform.python_version(), "onnx": onnx.__version__, "onnxruntime": ort.__version__, "sentencepiece": spm.__version__, "numpy": np.__version__,
        "artifacts": [{"path": str(p), "bytes": p.stat().st_size, "sha256": hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()} for p in (args.original, args.quantized, args.tokenizer)],
        "cases": len(corpus), "lexical_preservation_cases": len(rows),
        "exact_source_preservation_cases": len(rows),
        "whitespace_and_zwnj_preservation_cases": len(rows),
        "same_rendered_output_cases": sum(row["same_rendered_output"] for row in rows),
        "punctuated_cases": [sum(row[key] != row["input"] for row in rows) for key in ("original", "quantized")],
        "content_output_agreement": {name: {"same": same, "total": total, "ratio": same / total if total else 1} for name, (same, total) in agreements.items()},
        "post_punctuation_vs_fp32": {"tp": punctuation_tp, "fp": punctuation_fp, "fn": punctuation_fn,
                                     "f1_against_fp32_predictions": 2 * punctuation_tp / (2 * punctuation_tp + punctuation_fp + punctuation_fn) if punctuation_tp + punctuation_fp + punctuation_fn else 1},
        "cpu_single_thread_per_chunk_ms": [{"median": statistics.median(v), "max": max(v), "chunks": len(v)} for v in durations],
        "examples": rows,
    }
    encoded = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(encoded + "\n")
    print(encoded)

if __name__ == "__main__":
    main()
