from fastapi import FastAPI, List
from pydantic import BaseModel
import json
from datetime import datetime

app = FastAPI()

# This matches the 'StudySessionEntity' from your Android code
class StudySession(BaseModel):
    studentUsername: String
    subject: String
    timestamp: int
    institutionId: String
    deviceId: String

@app.get("/")
def read_root():
    return {"status": "Poke Bayou Fog Node Active", "location": "Local Prototyping"}

@app.post("/sync")
async fun receive_sync(sessions: List[StudySession]):
    # Open our 'State Master Log' and append the new data
    with open("arkansas_state_log.json", "a") as f:
        for session in sessions:
            log_entry = session.dict()
            log_entry["received_at"] = str(datetime.now())
            f.write(json.dumps(log_entry) + "\n")
    
    print(f"Successfully synced {len(sessions)} sessions to the Fog Node.")
    return {"message": "Sync Successful", "count": len(sessions)}

if __name__ == "__main__":
    import uvicorn
    # This runs the server on your laptop
    uvicorn.run(app, host="0.0.0.0", port=8000)