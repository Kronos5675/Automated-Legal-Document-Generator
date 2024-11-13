# LegalDocs

LegalDocs is a chatbot application that assists users in generating a legal Will document based on natural language input. It uses a BERT-based Named Entity Recognition (NER) model to extract essential details like the owner, executor, beneficiaries, and assets, and then formats these details into a structured legal document.

## Features
- Extracts key information from user input, such as owners, executors, beneficiaries, assets, and witnesses.
- Formats extracted information into a formal will document.
- Generates a PDF of the final will.

## Tech Stack
- **Backend**: Flask
- **Machine Learning**: BERT for NER, Google Gemini for text generation
- **PDF Generation**: FPDF
- **Deployment**: Android Application
