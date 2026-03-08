import streamlit as st
import json
import os
import pandas as pd
import plotly.express as px

CURRICULUM_FILE = "ged_curriculum_master.json"
SYNC_LOG_FILE = "arkansas_master_sync_log.json"

st.set_page_config(page_title="Arkansas Literacy Engine Admin", layout="wide")

def curriculum_manager():
    st.header("📖 GED Curriculum Deployment")
    
    if os.path.exists(CURRICULUM_FILE):
        with open(CURRICULUM_FILE, "r") as f:
            current_data = json.load(f)
    else:
        current_data = []

    with st.expander("➕ Add New GED Question", expanded=True):
        with st.form("new_question"):
            subject = st.selectbox("Subject", ["Social Studies", "Math", "Science", "Language Arts"])
            passage = st.text_area("Context/Passage", help="The text the student reads before the question.")
            question = st.text_input("The Question")
            
            col1, col2 = st.columns(2)
            with col1:
                optA = st.text_input("Option A")
                optB = st.text_input("Option B")
            with col2:
                optC = st.text_input("Option C")
                optD = st.text_input("Option D")
            
            correct = st.selectbox("Correct Answer", ["A", "B", "C", "D"])
            explanation = st.text_area("Socratic Explanation", help="Tutor's response on error/success.")
            
            if st.form_submit_button("🚀 Deploy to Fleet"):
                new_q = {
                    "subject": subject, "passage": passage, "question": question,
                    "optionA": optA, "optionB": optB, "optionC": optC, "optionD": optD,
                    "correctAnswer": correct, "explanation": explanation
                }
                current_data.append(new_q)
                with open(CURRICULUM_FILE, "w") as f:
                    json.dump(current_data, f, indent=4)
                st.success(f"New {subject} question pushed to Fog Node!")

    st.subheader("Current Active Modules")
    if current_data:
        for i, q in enumerate(current_data):
            st.info(f"**{i+1}. {q['subject']}**: {q['question']}")
    else:
        st.write("No questions deployed. Use the form above to start.")

def analytics_view():
    st.header("📊 Student Performance Heat Map")
    
    if os.path.exists(SYNC_LOG_FILE):
        with open(SYNC_LOG_FILE, "r") as f:
            data = json.load(f)
        df = pd.DataFrame(data)
    else:
        st.warning("No sync data found. Log a session on the tablet first.")
        return

    if not df.empty:
        subject_counts = df['subject'].value_counts().reset_index()
        subject_counts.columns = ['Subject', 'Attempted Sessions']

        fig = px.treemap(
            subject_counts, 
            path=['Subject'], 
            values='Attempted Sessions',
            color='Attempted Sessions',
            color_continuous_scale='Reds', 
            title="Curriculum Friction Levels"
        )
        st.plotly_chart(fig, use_container_with_width=True)

        st.subheader("LACES Compliance Log")
        st.dataframe(df, use_container_width=True)

# --- THE MAIN RENDER LOOP ---
st.title("Fleet Command: Jonesboro Admin Hub")

tab1, tab2 = st.tabs(["Curriculum Manager", "Analytics Heat Map"])

with tab1:
    curriculum_manager()
with tab2:
    analytics_view()