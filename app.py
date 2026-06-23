import streamlit as st
import PyPDF2
from google import genai

# --- PAGE CONFIG ---
st.set_page_config(page_title="AI Resume Analyzer", page_icon="📄", layout="wide")

# --- FUNCTIONS ---
def extract_text_from_pdf(uploaded_file):
    """Extracts text from an uploaded PDF file."""
    try:
        reader = PyPDF2.PdfReader(uploaded_file)
        text = ""
        for page in reader.pages:
            text += page.extract_text()
        return text
    except Exception as e:
        return f"Error reading PDF: {e}"

def analyze_resume(resume_text, job_description, api_key):
    """Calls the Gemini API to analyze the resume against the JD."""
    try:
        # Initialize the modern Google GenAI client
        client = genai.Client(api_key=api_key)
        
        prompt = f"""
        You are an expert Application Tracking System (ATS) and a career coach with deep knowledge of tech and corporate recruitment.
        
        Evaluate the following resume against the provided job description.
        
        Job Description:
        {job_description}
        
        Resume:
        {resume_text}
        
        Provide your analysis formatted clearly using Markdown. Structure your response EXACTLY like this:
        ### 🎯 Match Percentage
        [Provide a percentage score of how well the resume matches the JD, followed by a 1-sentence explanation]
        
        ### 🔍 Missing Keywords & Skills
        [List as bullet points the key skills, tools, or terms missing from the resume but required in the JD]
        
        ### 👤 Profile Summary
        [A brief, professional summary of the candidate's current fit for the role]
        
        ### 💡 Actionable Improvements
        [Provide 3-4 highly specific, actionable recommendations to improve the resume for this exact role]
        """
        
        # Using Gemini 2.5 Flash for fast, accurate NLP reasoning
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
        )
        return response.text
    except Exception as e:
        return f"Error communicating with AI: {e}"

# --- MAIN APP UI ---
st.title("📄 AI-Powered Resume Analyzer")
st.markdown("Evaluate your resume, match it to job roles, and get actionable improvements using AI.")

# Sidebar for API Key
st.sidebar.header("⚙️ Configuration")
api_key_input = st.sidebar.text_input("Enter your Google Gemini API Key:", type="password")
st.sidebar.markdown("Don't have a key? [Get one here for free](https://aistudio.google.com/app/apikey).")
st.sidebar.markdown("---")
st.sidebar.info("This app runs locally. Your API key and resume data are not stored permanently.")

# Main input sections
col1, col2 = st.columns(2)

with col1:
    st.subheader("1. Job Description")
    jd_input = st.text_area("Paste the target Job Description here:", height=300)

with col2:
    st.subheader("2. Resume Upload")
    uploaded_resume = st.file_uploader("Upload your resume (PDF format only)", type=["pdf"])

# Execution button
st.markdown("---")
if st.button("Analyze Resume 🚀", use_container_width=True):
    if not api_key_input:
        st.error("Please enter your Gemini API Key in the sidebar.")
    elif not jd_input.strip():
        st.warning("Please provide a Job Description.")
    elif uploaded_resume is None:
        st.warning("Please upload a resume in PDF format.")
    else:
        with st.spinner("Parsing resume and generating AI analysis..."):
            extracted_text = extract_text_from_pdf(uploaded_resume)
            
            if "Error reading PDF" in extracted_text:
                st.error(extracted_text)
            else:
                analysis_result = analyze_resume(extracted_text, jd_input, api_key_input)
                
                st.success("Analysis Complete!")
                st.markdown("### 📊 AI Analysis Report")
                st.markdown(analysis_result)
