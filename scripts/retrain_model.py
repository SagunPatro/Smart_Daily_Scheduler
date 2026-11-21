from ml_duration_model import train_model

if __name__ == "__main__":
    print("🔁 Retraining model from user feedback...")
    train_model("user_logs.csv")
    print("✅ Retraining complete. Model updated.")
