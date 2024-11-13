import torch
from transformers import BertTokenizer, BertForTokenClassification
from collections import defaultdict
from datetime import datetime
import google.generativeai as genai
from fpdf import FPDF, XPos, YPos

# Configure the Gemini API key
genai.configure(api_key="AIzaSyBBrSYZT3Auwl6Nx3V29sbnzLo29UBDGrA")

# Load the trained model and tokenizer
model = BertForTokenClassification.from_pretrained("will_ner_model")
tokenizer = BertTokenizer.from_pretrained("will_ner_model")
model.eval()  # Set model to evaluation mode

LABELS = ["O", "Owner", "Executor", "Asset", "Owner Address", "Asset Address",
          "Beneficiary Relation", "Beneficiary", "Witness"]

class WillChatbot:
    def __init__(self):
        self.user_data = {
            "owner": None,
            "executor": None,
            "beneficiary": [],
            "assets": [],
            "owner_address": None,
            "asset_address": [],
            "beneficiary_relation": [],
            "witness": []
        }
        self.input_text = ""  # Store main input text with appended info

    def welcome(self):
        print("Welcome to the Legal Document Generator!")
        print("Please select the type of document you want to generate:")
        print("1. Will")
        choice = input("Enter the number of your choice: ")

        if choice == "1":
            print("Great! Let's get started with creating your Will.")
            self.get_paragraph_input()
        else:
            print("Sorry, currently we only support generating Wills.")

    def get_paragraph_input(self):
        self.input_text = input("Please Provide the Information you want in the Will: ")
        self.process_input(self.input_text)

    def process_input(self, input_text):
        predictions = self.predict_ner(input_text)
        formatted_ner_data = self.format_ner_output(predictions)

        # Check for missing information
        missing_info = self.check_missing_info(formatted_ner_data)

        # Prompt for missing info in a conversational way
        if missing_info:
            print("\nIt seems we're missing some details to complete your will.")
            self.prompt_for_missing_info(missing_info)

        # Update user data with extracted information
        self.user_data.update(formatted_ner_data)
        self.generate_will()  # Use self.input_text here

    def predict_ner(self, text):
        tokens = tokenizer.tokenize(text)
        input_ids = tokenizer.encode(text, return_tensors="pt")

        with torch.no_grad():
            output = model(input_ids)
            logits = output.logits
            predictions = torch.argmax(logits, dim=2)

        predicted_labels = [LABELS[pred] for pred in predictions[0].numpy()]
        tokens_with_labels = [(token, label) for token, label in zip(tokens, predicted_labels[1:len(tokens) + 1])]

        return tokens_with_labels

    def format_ner_output(self, predictions):
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

        # Simplify the output format for certain fields
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

    def check_missing_info(self, formatted_ner_data):
        # Identify only fields that are None or empty lists for prompting
        missing = [label for label, value in formatted_ner_data.items() if not value]
        return missing

    def prompt_for_missing_info(self, missing_info):
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

        # Templates for creating full sentences based on user input
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

        additional_sentences = []  # List to store formatted sentences to append to main input text

        for label in missing_info:
            prompt = prompts.get(label, "Please provide the missing information.")
            response = input(prompt + " ")

            # Save response and format it
            if isinstance(self.user_data[label], list):
                self.user_data[label].append(response)
            else:
                self.user_data[label] = response

            # Format the response into a full sentence and store it
            formatted_sentence = templates[label].format(response=response)
            additional_sentences.append(formatted_sentence)

        # Append all formatted sentences to the main input text
        self.input_text += " " + " ".join(additional_sentences)

    def prepare_gemini_prompt(self):
        current_date = datetime.now().strftime("%B %d, %Y")

        summary = [f"{label.replace('_', ' ').capitalize()}: {info}" for label, info in self.user_data.items() if info]
        ner_summary = "\n".join(summary)

        prompt = (
            f"Convert the following informal paragraph into a formal legal will:\n\n"
            f"Informal Paragraph: {self.input_text}\n\n"
            f"Identified Details:\n{ner_summary}\n\n"
            "Please format the will as follows:\n"
            "- Only include sentences for the information that is present.\n"
            "- Don't generate any sort of markdown formatting, just plain text.\n"
            "- Wherever signatures are required, just use the name instead of '[Signature of...].'\n"
            "- Do not include placeholder text like '[address of Beneficiary]' if the address is not provided.\n"
            "- Don't include any currency symbols; instead, write the currency as text (e.g., 'Rs.').\n"
            f"- Use the date {current_date} in the 'Date' field.\n\n"
            "Generate the formal will based on these instructions."
        )
        return prompt

    def generate_will(self):
        gemini_prompt = self.prepare_gemini_prompt()
        model = genai.GenerativeModel("gemini-1.5-flash")
        response = model.generate_content(gemini_prompt)

        will_text = response.text
        self.generate_pdf(will_text)

    def generate_pdf(self, will_text, filename="Legal_Will.pdf"):
        pdf = FPDF()
        pdf.add_page()
        pdf.set_auto_page_break(auto=True, margin=15)

        # Set the font to Helvetica instead of Arial
        pdf.set_font("helvetica", "B", 16)
        pdf.cell(200, 10, "Last Will and Testament", new_x=XPos.LMARGIN, new_y=YPos.NEXT, align="C")
        pdf.ln(10)

        pdf.set_font("helvetica", "", 12)
        pdf.multi_cell(0, 10, will_text)

        pdf.output(filename)
        print(f"PDF saved as '{filename}'.")

# Start the chatbot
chatbot = WillChatbot()
chatbot.welcome()
