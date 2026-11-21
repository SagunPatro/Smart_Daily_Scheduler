from ml_duration_model import train_model

if __name__ == "__main__":
    train_model("user_logs.csv")
    print("✅ Model retrained using feedback data")
