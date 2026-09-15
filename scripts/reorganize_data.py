import os
import shutil
import json

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD_DATA_DIR = os.path.join(BASE_DIR, "data")
NEW_DATA_DIR = os.path.join(BASE_DIR, "data")
DOCS_DIR = os.path.join(BASE_DIR, "docs")

# Define target paths
PATHS = {
    "raw_books": os.path.join(NEW_DATA_DIR, "raw", "books"),
    "raw_scholarly": os.path.join(NEW_DATA_DIR, "raw", "scholarly"),
    "raw_documents": os.path.join(NEW_DATA_DIR, "raw", "documents"),
    
    "processed_books": os.path.join(NEW_DATA_DIR, "processed", "books"),
    "processed_scholarly": os.path.join(NEW_DATA_DIR, "processed", "scholarly"),
    "processed_documents": os.path.join(NEW_DATA_DIR, "processed", "documents"),
    "processed_docs_text": os.path.join(NEW_DATA_DIR, "processed", "documents", "extracted_text"),
    
    "synthetic_users": os.path.join(NEW_DATA_DIR, "synthetic", "users"),
    "synthetic_reading": os.path.join(NEW_DATA_DIR, "synthetic", "reading_history"),
    "synthetic_favorites": os.path.join(NEW_DATA_DIR, "synthetic", "favorites"),
    "synthetic_search": os.path.join(NEW_DATA_DIR, "synthetic", "search_history"),
    
    "docs": DOCS_DIR
}

for p in PATHS.values():
    os.makedirs(p, exist_ok=True)

def move_file(src, dst):
    if os.path.exists(src):
        shutil.move(src, dst)
        print(f"Moved: {src} -> {dst}")

def copy_file(src, dst):
    if os.path.exists(src):
        shutil.copy2(src, dst)
        print(f"Copied: {src} -> {dst}")

def main():
    print("Re-organizing AstraLib Datasets into finalized structure...")
    
    # 1. Books
    move_file(os.path.join(OLD_DATA_DIR, "books", "books_raw.json"), os.path.join(PATHS["raw_books"], "books_raw.json"))
    move_file(os.path.join(OLD_DATA_DIR, "books", "books_cleaned.json"), os.path.join(PATHS["processed_books"], "books_cleaned.json"))
    move_file(os.path.join(OLD_DATA_DIR, "books", "metadata.json"), os.path.join(PATHS["processed_books"], "metadata.json"))
    
    # 2. Scholarly
    move_file(os.path.join(OLD_DATA_DIR, "scholarly", "scholarly_raw.json"), os.path.join(PATHS["raw_scholarly"], "scholarly_raw.json"))
    move_file(os.path.join(OLD_DATA_DIR, "scholarly", "scholarly_cleaned.json"), os.path.join(PATHS["processed_scholarly"], "scholarly_cleaned.json"))
    move_file(os.path.join(OLD_DATA_DIR, "scholarly", "metadata.json"), os.path.join(PATHS["processed_scholarly"], "metadata.json"))
    
    # Processed Graph nodes/edges -> scholarly processed
    move_file(os.path.join(OLD_DATA_DIR, "processed", "knowledge_graph_nodes.json"), os.path.join(PATHS["processed_scholarly"], "knowledge_graph_nodes.json"))
    move_file(os.path.join(OLD_DATA_DIR, "processed", "knowledge_graph_edges.json"), os.path.join(PATHS["processed_scholarly"], "knowledge_graph_edges.json"))
    
    # 3. Documents
    move_file(os.path.join(OLD_DATA_DIR, "documents", "documents_catalog.json"), os.path.join(PATHS["raw_documents"], "documents_catalog.json"))
    move_file(os.path.join(OLD_DATA_DIR, "documents", "documents_full_rag.json"), os.path.join(PATHS["processed_documents"], "documents_full_rag.json"))
    move_file(os.path.join(OLD_DATA_DIR, "processed", "rag_chunks_sample.json"), os.path.join(PATHS["processed_documents"], "rag_chunks_sample.json"))
    move_file(os.path.join(OLD_DATA_DIR, "documents", "metadata.json"), os.path.join(PATHS["processed_documents"], "metadata.json"))
    
    # Move extracted_text .md files
    old_text_dir = os.path.join(OLD_DATA_DIR, "documents", "extracted_text")
    if os.path.exists(old_text_dir):
        for f in os.listdir(old_text_dir):
            shutil.move(os.path.join(old_text_dir, f), os.path.join(PATHS["processed_docs_text"], f))
        shutil.rmtree(old_text_dir)
        print("Moved extracted_text markdown files.")
        
    # 4. Synthetic Users
    move_file(os.path.join(OLD_DATA_DIR, "users", "users.json"), os.path.join(PATHS["synthetic_users"], "users.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "user_interests.json"), os.path.join(PATHS["synthetic_users"], "user_interests.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "reading_history.json"), os.path.join(PATHS["synthetic_reading"], "reading_history.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "favorites.json"), os.path.join(PATHS["synthetic_favorites"], "favorites.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "search_history.json"), os.path.join(PATHS["synthetic_search"], "search_history.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "interactions.json"), os.path.join(PATHS["synthetic_search"], "interactions.json"))
    move_file(os.path.join(OLD_DATA_DIR, "users", "metadata.json"), os.path.join(PATHS["synthetic_users"], "metadata.json"))
    
    # Unified Catalog -> processed root
    move_file(os.path.join(OLD_DATA_DIR, "processed", "unified_catalog.json"), os.path.join(NEW_DATA_DIR, "processed", "unified_catalog.json"))
    
    # 5. Documentation
    move_file(os.path.join(OLD_DATA_DIR, "DATASET_README.md"), os.path.join(DOCS_DIR, "DATASET_README.md"))
    
    # Clean up old empty subfolders
    for d in ["books", "scholarly", "documents", "users", "processed"]:
        old_path = os.path.join(OLD_DATA_DIR, d)
        if os.path.exists(old_path) and not os.listdir(old_path):
            os.rmdir(old_path)
            print(f"Removed old folder: {old_path}")
            
    print("Dataset Re-organization Successfully Completed!")

if __name__ == "__main__":
    main()
