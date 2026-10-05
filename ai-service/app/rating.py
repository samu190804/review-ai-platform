from transformers import pipeline

classifier = pipeline("sentiment-analysis", 
                      model="nlptown/bert-base-multilingual-uncased-sentiment")
def rate(review: str) -> dict:
    result = classifier(review.text[:512])[0]
    return result
