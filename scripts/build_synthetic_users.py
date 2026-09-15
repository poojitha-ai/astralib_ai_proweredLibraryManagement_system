import json
import os
import random
from datetime import datetime, timedelta

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "data", "users")
os.makedirs(OUTPUT_DIR, exist_ok=True)

random.seed(42)

FIRST_NAMES = ["Aarav", "Ananya", "Rohan", "Priya", "Vikram", "Neha", "Siddharth", "Kavya", "Aditya", "Meera",
               "Alex", "Sophia", "Liam", "Emma", "Noah", "Olivia", "Ethan", "Ava", "Lucas", "Isabella",
               "Chen", "Wei", "Yuki", "Hao", "Mei", "Tariq", "Fatima", "Zaid", "Aisha", "Omar"]
LAST_NAMES = ["Sharma", "Verma", "Gupta", "Patel", "Rao", "Nair", "Iyer", "Deshmukh", "Joshi", "Kulkarni",
              "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
              "Wang", "Li", "Zhang", "Tanaka", "Sato", "Khan", "Ahmed", "Ali", "Hassan", "Hussein"]

DEPARTMENTS = ["Computer Science & Engineering", "Artificial Intelligence & Data Science", "Information Technology", "Electrical & Computer Engineering", "Computational Linguistics"]
ROLES = ["Undergraduate Student", "Postgraduate Student", "PhD Scholar", "Assistant Professor", "Associate Professor", "Research Fellow"]
INTEREST_TOPICS = [
    "Deep Learning", "Natural Language Processing", "Computer Vision", "Reinforcement Learning",
    "Distributed Systems", "Database Systems", "Software Engineering", "Cybersecurity",
    "Algorithms & Data Structures", "Cloud Computing", "Quantum Computing", "Bioinformatics",
    "Graph Neural Networks", "Large Language Models", "Robotics", "Human-Computer Interaction"
]
SEARCH_QUERIES = [
    "transformer architecture attention mechanisms", "distributed consensus raft vs paxos",
    "graph neural networks node classification", "retrieval augmented generation vector database",
    "bert fine tuning text classification tutorial", "quantized llm inference llama 3",
    "zero knowledge proofs cryptography introduction", "microservices communication grpc protobuf",
    "compiler design intermediate representation", "autonomous navigation simultaneous localization and mapping",
    "deep reinforcement learning q learning policy gradient", "explainable ai shap lime model interpretability",
    "operating system kernel thread scheduling", "cloud native kubernetes operator pattern"
]

def generate_users(count=200):
    users = []
    user_interests = []
    for i in range(1, count + 1):
        u_id = f"USR-{i:04d}"
        fname = random.choice(FIRST_NAMES)
        lname = random.choice(LAST_NAMES)
        username = f"{fname.lower()}.{lname.lower()}{random.randint(10,99)}"
        email = f"{username}@astralib.edu"
        role = random.choice(ROLES)
        dept = random.choice(DEPARTMENTS)
        created_days_ago = random.randint(10, 365)
        created_at = (datetime.now() - timedelta(days=created_days_ago)).isoformat()
        
        users.append({
            "user_id": u_id,
            "username": username,
            "email": email,
            "full_name": f"{fname} {lname}",
            "role": role,
            "department": dept,
            "academic_level": role,
            "created_at": created_at
        })
        
        # User interests
        interests = random.sample(INTEREST_TOPICS, k=random.randint(2, 5))
        user_interests.append({
            "user_id": u_id,
            "primary_interests": interests,
            "preferred_resource_types": random.sample(["book", "paper", "document"], k=random.randint(1, 3))
        })
        
    return users, user_interests

def generate_user_activities(users, book_ids, paper_ids, doc_ids):
    all_resources = [(b, "book") for b in book_ids] + [(p, "paper") for p in paper_ids] + [(d, "document") for d in doc_ids]
    
    reading_history = []
    favorites = []
    search_history = []
    interactions = []
    
    history_idx = 1
    fav_idx = 1
    search_idx = 1
    inter_idx = 1
    
    now = datetime.now()
    
    for u in users:
        u_id = u["user_id"]
        
        # 1. Reading History (approx 25 entries per user -> 5,000 total)
        num_reads = random.randint(18, 32)
        user_res_sample = random.sample(all_resources, k=min(len(all_resources), num_reads))
        for res_id, res_type in user_res_sample:
            read_days_ago = random.randint(1, 90)
            timestamp = (now - timedelta(days=read_days_ago, minutes=random.randint(0, 1440))).isoformat()
            duration = random.randint(120, 7200) # 2 mins to 2 hours
            completion = random.choice([25, 50, 75, 100, 100, 100])
            
            reading_history.append({
                "history_id": f"HIST-{history_idx:06d}",
                "user_id": u_id,
                "resource_id": res_id,
                "resource_type": res_type,
                "timestamp": timestamp,
                "duration_seconds": duration,
                "completion_percentage": completion
            })
            history_idx += 1
            
            # Interaction log
            interactions.append({
                "interaction_id": f"INT-{inter_idx:06d}",
                "user_id": u_id,
                "resource_id": res_id,
                "resource_type": res_type,
                "action": random.choice(["view", "download", "read", "cite"]),
                "timestamp": timestamp
            })
            inter_idx += 1
            
        # 2. Favorites (approx 7-8 per user -> 1,500 total)
        num_favs = random.randint(5, 10)
        fav_sample = random.sample(user_res_sample, k=min(len(user_res_sample), num_favs))
        for res_id, res_type in fav_sample:
            fav_days_ago = random.randint(1, 60)
            added_at = (now - timedelta(days=fav_days_ago)).isoformat()
            favorites.append({
                "favorite_id": f"FAV-{fav_idx:06d}",
                "user_id": u_id,
                "resource_id": res_id,
                "resource_type": res_type,
                "added_at": added_at
            })
            fav_idx += 1
            
            interactions.append({
                "interaction_id": f"INT-{inter_idx:06d}",
                "user_id": u_id,
                "resource_id": res_id,
                "resource_type": res_type,
                "action": "bookmark",
                "timestamp": added_at
            })
            inter_idx += 1

        # 3. Search History (approx 15 per user -> 3,000 total)
        num_searches = random.randint(12, 18)
        for _ in range(num_searches):
            search_days_ago = random.randint(1, 90)
            search_time = (now - timedelta(days=search_days_ago, minutes=random.randint(0, 1440))).isoformat()
            q_text = random.choice(SEARCH_QUERIES)
            search_mode = random.choice(["keyword", "semantic", "rag_chat"])
            clicked_res = random.choice(user_res_sample)[0] if random.random() > 0.2 else None
            
            search_history.append({
                "search_id": f"SRCH-{search_idx:06d}",
                "user_id": u_id,
                "query_text": q_text,
                "search_type": search_mode,
                "filters_used": {"language": "eng", "year_min": 2018},
                "timestamp": search_time,
                "clicked_resource_id": clicked_res
            })
            search_idx += 1
            
            if clicked_res:
                interactions.append({
                    "interaction_id": f"INT-{inter_idx:06d}",
                    "user_id": u_id,
                    "resource_id": clicked_res,
                    "resource_type": "resource",
                    "action": "search_click",
                    "timestamp": search_time
                })
                inter_idx += 1

    return reading_history, favorites, search_history, interactions

def main():
    print("Generating Synthetic User Dataset for AstraLib...")
    users, user_interests = generate_users(200)
    
    # Load resource IDs for relation linking
    books_file = os.path.join(PROJECT_ROOT, "data", "books", "books_cleaned.json")
    scholarly_file = os.path.join(PROJECT_ROOT, "data", "scholarly", "scholarly_cleaned.json")
    doc_file = os.path.join(PROJECT_ROOT, "data", "documents", "documents_catalog.json")
    
    book_ids = [f"BOOK-SYNTH-{i:05d}" for i in range(1, 3001)]
    paper_ids = [f"PAPER-SYNTH-{i:05d}" for i in range(1, 3001)]
    doc_ids = [f"DOC-TECH-{i:03d}" for i in range(1, 101)]
    
    if os.path.exists(books_file):
        with open(books_file, "r", encoding="utf-8") as f:
            book_ids = [b["resource_id"] for b in json.load(f)]
    if os.path.exists(scholarly_file):
        with open(scholarly_file, "r", encoding="utf-8") as f:
            paper_ids = [p["resource_id"] for p in json.load(f)]
    if os.path.exists(doc_file):
        with open(doc_file, "r", encoding="utf-8") as f:
            doc_ids = [d["document_id"] for d in json.load(f)]
            
    reading_hist, favs, searches, interactions = generate_user_activities(users, book_ids, paper_ids, doc_ids)
    
    with open(os.path.join(OUTPUT_DIR, "users.json"), "w", encoding="utf-8") as f:
        json.dump(users, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(OUTPUT_DIR, "user_interests.json"), "w", encoding="utf-8") as f:
        json.dump(user_interests, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(OUTPUT_DIR, "reading_history.json"), "w", encoding="utf-8") as f:
        json.dump(reading_hist, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(OUTPUT_DIR, "favorites.json"), "w", encoding="utf-8") as f:
        json.dump(favs, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(OUTPUT_DIR, "search_history.json"), "w", encoding="utf-8") as f:
        json.dump(searches, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(OUTPUT_DIR, "interactions.json"), "w", encoding="utf-8") as f:
        json.dump(interactions, f, indent=2, ensure_ascii=False)
        
    print(f"Synthetic User Generation Complete:")
    print(f" - Users: {len(users)}")
    print(f" - Interests: {len(user_interests)}")
    print(f" - Reading Logs: {len(reading_hist)}")
    print(f" - Favorites: {len(favs)}")
    print(f" - Searches: {len(searches)}")
    print(f" - Interactions: {len(interactions)}")

if __name__ == "__main__":
    main()
