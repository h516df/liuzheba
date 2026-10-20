"""Synthetic CPU validation only. No network, private data, or Android acceptance.
Install onnxruntime==1.25.0 and tokenizers==0.22.2 in an isolated environment.
Usage: python evaluate_embeddings.py /path/to/model.onnx /path/to/tokenizer.json
Pinned files and hashes: docs/LOCAL_SEARCH_ASSISTANT.md.
"""
import hashlib
import json
import sys
from pathlib import Path
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

model, tokenizer_file = map(Path, sys.argv[1:3])
assert hashlib.sha256(model.read_bytes()).hexdigest() == "b9837c19ce154ff0726d398ee77abbc03a7faf0476c6f93016c84e531be7ebb5"
assert hashlib.sha256(tokenizer_file.read_bytes()).hexdigest() == "48cea5d44424912a6fd1ea647bf4fe50b55ab8b1e5879c3275f80e339e8fae26"
options = ort.SessionOptions()
options.intra_op_num_threads = 2
session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
assert {x.name for x in session.get_inputs()} == {"input_ids", "attention_mask", "token_type_ids"}
assert session.get_outputs()[0].name == "last_hidden_state"
tokenizer = Tokenizer.from_file(str(tokenizer_file))
tokenizer.enable_truncation(512)

def embed(text):
    ids = np.array([tokenizer.encode(text).ids], dtype=np.int64)
    vector = session.run(None, {"input_ids": ids, "attention_mask": np.ones_like(ids), "token_type_ids": np.zeros_like(ids)})[0][0, 0]
    assert vector.shape == (512,) and np.isfinite(vector).all()
    vector = vector / np.linalg.norm(vector)
    assert abs(float(np.linalg.norm(vector)) - 1) < 1e-5
    return vector

documents = [
    "在登录页面选择找回密码，通过短信验证码重置。",
    "从设置打开备份，导出记录为文件并保存。",
    "番茄炒蛋的做法：先炒鸡蛋，再加入番茄。",
    "春天去公园散步，天气晴朗。",
    "保存在书架上的古诗文集。",
]
vectors = np.stack([embed(text) for text in documents])
report = []
for query, expected in [("忘记登录密码怎么办", 0), ("怎么把记录导出来", 1), ("做一道鸡蛋菜", 2), ("宇宙飞船的燃料是什么", None)]:
    scores = vectors @ embed("为这个句子生成表示以用于检索相关文章：" + query)
    if expected is None:
        assert float(max(scores)) < 0.56
    else:
        assert int(np.argmax(scores)) == expected and float(scores[expected]) >= 0.56
    report.append({"query": query, "expected": expected, "scores": scores.tolist()})
print(json.dumps({"runtime": ort.__version__, "platform": "cloud CPU; NOT Android", "cases": report}, ensure_ascii=False, indent=2))
