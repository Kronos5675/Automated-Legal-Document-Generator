from flask import Flask, request, jsonify, send_file
import torch
from transformers import BertTokenizer, BertForTokenClassification
from collections import defaultdict
from datetime import datetime
import google.generativeai as genai
from fpdf import FPDF
import os
import requests
import zipfile

# Configure the Gemini API key
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

# Constants for model handling
MODEL_URL = "https://github.com/Kronos5675/Legal-Language-Generator/releases/download/v1.0/will_ner_model.zip"
MODEL_DIR = "will_ner_model/will_ner_model"

# Function to download and extract the model
def download_model():
    if not os.path.exists(MODEL_DIR):
        os.makedirs(MODEL_DIR)
        response = requests.get(MODEL_URL)
        model_zip_path = os.path.join(MODEL_DIR, "will_ner_model.zip")
        with open(model_zip_path, "wb") as f:
            f.write(response.content)
        # Extract the model
        with zipfile.ZipFile(model_zip_path, 'r') as zip_ref:
            zip_ref.extractall(MODEL_DIR)
        os.remove(model_zip_path)  # Clean up zip file after extraction

# Load the trained model and tokenizer
download_model()  # Ensure model is downloaded before loading
model = BertForTokenClassification.from_pretrained(MODEL_DIR)
tokenizer = BertTokenizer.from_pretrained(MODEL_DIR)
model.eval()  # Set model to evaluation mode

LABELS = ["O", "Owner", "Executor", "Asset", "Owner Address", "Asset Address",
          "Beneficiary Relation", "Beneficiary", "Witness"]

# Initialize Flask app
app = Flask(__name__)

# Helper functions for NER and PDF generation
def predict_ner(text):
    tokens = tokenizer.tokenize(text)
    input_ids = tokenizer.encode(text, return_tensors="pt")

    with torch.no_grad():
        output = model(input_ids)
        logits = output.logits
        predictions = torch.argmax(logits, dim=2)

    predicted_labels = [LABELS[pred] for pred in predictions[0].numpy()]
    tokens_with_labels = [(token, label) for token, label in zip(tokens, predicted_labels[1:len(tokens) + 1])]

    return tokens_with_labels

def format_ner_output(predictions):
    structured_output = defaultdict(list)
    current_entity = None
    current_tokens = []

    for token, label in predictions:
        if label != "O":
            if current_entity and label != current_entity:
                structured_output[current_entity].append(" ".join(current_tokens))
                current_tokens = []

            current_entity = label
            current_tokens.append(token)
        else:
            if current_entity:
                structured_output[current_entity].append(" ".join(current_tokens))
                current_entity = None
                current_tokens = []

    if current_entity:
        structured_output[current_entity].append(" ".join(current_tokens))

    formatted_output = {
        "owner": " ".join(structured_output["Owner"]) if structured_output["Owner"] else None,
        "executor": " ".join(structured_output["Executor"]) if structured_output["Executor"] else None,
        "beneficiary": structured_output["Beneficiary"],
        "assets": structured_output["Asset"],
        "owner_address": " ".join(structured_output["Owner Address"]) if structured_output["Owner Address"] else None,
        "asset_address": structured_output["Asset Address"],
        "beneficiary_relation": structured_output["Beneficiary Relation"],
        "witness": structured_output["Witness"]
    }

    return formatted_output

def generate_pdf(content, filename="Legal_Will.pdf"):
    pdf = FPDF()
    pdf.add_page()
    pdf.set_auto_page_break(auto=True, margin=15)

    pdf.set_font("helvetica", "B", 16)
    pdf.cell(200, 10, "Last Will and Testament", align="C", ln=True)
    pdf.ln(10)

    pdf.set_font("helvetica", "", 12)
    pdf.multi_cell(0, 10, content)

    pdf.output(filename)
    return filename

# Templates for missing information prompts
prompts = {
    "owner": "Could you please specify the name of the person making this will?",
    "executor": "Who would you like to appoint as the executor of this will?",
    "beneficiary": "Please list the beneficiaries and what they should receive.",
    "assets": "Could you describe the assets you'd like to include in the will?",
    "owner_address": "What is the address of the will's owner?",
    "asset_address": "Can you provide the addresses for any assets mentioned?",
    "beneficiary_relation": "What is the relationship of each beneficiary to you?",
    "witness": "Who would you like to list as witnesses for this will?"
}

# Templates to format responses into sentences for the will
templates = {
    "owner": "My name is {response}.",
    "executor": "The executor of my will is {response}.",
    "beneficiary": "I wish to leave certain assets to {response}.",
    "assets": "The assets in my will include {response}.",
    "owner_address": "My address is {response}.",
    "asset_address": "The address of the asset is {response}.",
    "beneficiary_relation": "The relationship to the beneficiary is {response}.",
    "witness": "The witness of my will is {response}."
}

# Endpoint to process input and generate will
@app.route('/generate_will', methods=['POST'])
def generate_will():
    data = request.json
    input_text = data.get("text", "")
    predictions = predict_ner(input_text)
    formatted_ner_data = format_ner_output(predictions)

    # Check for missing information
    missing_info = [label for label, value in formatted_ner_data.items() if not value]
    additional_sentences = []

    if missing_info:
        # Prompt client to provide missing information for each required field
        missing_prompts = {label: prompts[label] for label in missing_info}
        return jsonify({"message": "Missing required information", "missing_info": missing_prompts}), 400

    # If additional information provided in the request, update the data and format sentences
    for label, response in data.get("additional_info", {}).items():
        if label in templates:
            formatted_sentence = templates[label].format(response=response)
            additional_sentences.append(formatted_sentence)

    # Update input_text with additional sentences from the prompt responses
    input_text += " " + " ".join(additional_sentences)

    # Prepare the Gemini prompt
    current_date = datetime.now().strftime("%B %d, %Y")
    summary = [f"{label.replace('_', ' ').capitalize()}: {info}" for label, info in formatted_ner_data.items() if info]
    ner_summary = "\n".join(summary)

    prompt = (
        f"Convert the following informal paragraph into a formal legal will:\n\n"
        f"Informal Paragraph: {input_text}\n\n"
        f"Identified Details:\n{ner_summary}\n\n"
        "Please format the will as follows:\n"
        "- Only include sentences for the information that is present.\n"
        "- Wherever signatures are required, just use the name instead of '[Signature of...].'\n"
        f"- Use the date {current_date} in the 'Date' field.\n\n"
        "Generate the formal will based on these instructions."
    )

    # Use the Gemini model to generate will text
    model = genai.GenerativeModel("gemini-1.5-flash")
    response = model.generate_content(prompt)
    will_text = response.text

    # Generate PDF
    pdf_filename = generate_pdf(will_text)
    return send_file(pdf_filename, as_attachment=True)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
