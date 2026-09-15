package com.nameemrooz.journal.speech;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression test: the production Shenava profile must initialize the real RNNT model. */
public final class RnntModelStartupTest {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: RnntModelStartupTest MODEL_DIR");
    }
    Path modelDir = Path.of(args[0]).toAbsolutePath();
    Path encoder = required(modelDir.resolve("encoder.int8.onnx"));
    Path decoder = required(modelDir.resolve("decoder.int8.onnx"));
    Path joiner = required(modelDir.resolve("joiner.int8.onnx"));
    Path tokens = required(modelDir.resolve("tokens.txt"));

    OfflineTransducerModelConfig transducer = OfflineTransducerModelConfig.builder()
        .setEncoder(encoder.toString())
        .setDecoder(decoder.toString())
        .setJoiner(joiner.toString())
        .build();
    OfflineModelConfig model = OfflineModelConfig.builder()
        .setTransducer(transducer)
        .setTokens(tokens.toString())
        .setNumThreads(2)
        .setDebug(false)
        .setProvider("cpu")
        .setModelType(RnntModelSpec.MODEL_TYPE)
        .build();
    OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
        .setFeatureConfig(FeatureConfig.builder().setSampleRate(16000).setFeatureDim(80).build())
        .setOfflineModelConfig(model)
        .setDecodingMethod("greedy_search")
        .build();

    OfflineRecognizer recognizer = new OfflineRecognizer(config);
    recognizer.release();
    System.out.println("Shenava RNNT initialized as " + RnntModelSpec.MODEL_TYPE);
  }

  private static Path required(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Missing model asset: " + path);
    }
    return path;
  }
}
