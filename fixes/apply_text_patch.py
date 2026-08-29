from pathlib import Path

path = Path("app/src/main/java/com/nameemrooz/journal/util/PersianText.kt")
text = path.read_text(encoding="utf-8")

old = '''        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گی" to "می‌گی", "میگی" to "می‌گی"
'''
new = '''        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گی" to "می‌گی", "میگی" to "می‌گی",
        "می رفتم" to "می‌رفتم", "میرفتم" to "می‌رفتم",
        "می گفتم" to "می‌گفتم", "میگفتم" to "می‌گفتم",
        "نمی دونستم" to "نمی‌دونستم", "نمیدونستم" to "نمی‌دونستم",
        "می اومدم" to "می‌اومدم", "میومدم" to "می‌اومدم",
        "می خاستم" to "می‌خواستم", "میخاستم" to "می‌خواستم",
        "نمی فهمیدم" to "نمی‌فهمیدم", "نمیفهمیدم" to "نمی‌فهمیدم"
'''

count = text.count(old)
if count != 1:
    raise SystemExit(f"colloquial phrase anchor: expected exactly one match, got {count}")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("TEXT_PATCH_OK")