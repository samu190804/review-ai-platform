from transformers import pipeline

classifier = pipeline("sentiment-analysis", 
                      model="nlptown/bert-base-multilingual-uncased-sentiment")

def rate(review: str) -> dict:
    predict = classifier(review[:512])[0]
    result = {"stars": int(predict["label"][0]), "confidence": round(predict["score"], 2)}
    
    return result
