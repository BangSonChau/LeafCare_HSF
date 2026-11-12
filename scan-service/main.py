import io
import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from labels import LABELS

MAX_BYTES = 5 * 1024 * 1024  # shortcut: 5 MB cap, raise if users send big photos

opts = ort.SessionOptions()
opts.intra_op_num_threads = 2
session = ort.InferenceSession("model.onnx", opts, providers=["CPUExecutionProvider"])

app = FastAPI()


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/predict")
async def predict(image: UploadFile = File(...)):
    data = await image.read()
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "Anh qua lon (toi da 5 MB)")
    try:
        img = Image.open(io.BytesIO(data)).convert("RGB").resize((224, 224))
    except UnidentifiedImageError:
        raise HTTPException(400, "File khong phai anh")
    # same as torchvision to_tensor: CHW, pixel 0..1, no Normalize
    x = (np.asarray(img, dtype=np.float32) / 255.0).transpose(2, 0, 1)[None]
    logits = session.run(None, {"x": x})[0][0]
    probs = np.exp(logits - logits.max())
    probs /= probs.sum()
    top3 = [{"label": LABELS[i], "confidence": round(float(probs[i]), 4)}
            for i in probs.argsort()[::-1][:3]]
    return {**top3[0], "top3": top3,
            "probs": {LABELS[i]: round(float(probs[i]), 6) for i in range(len(LABELS))},
            "isLeaf": top3[0]["label"] != "Background_without_leaves",
            "isHealthy": top3[0]["label"].endswith("healthy")}
