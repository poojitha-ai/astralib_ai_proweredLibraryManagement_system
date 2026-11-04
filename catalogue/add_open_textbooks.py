#!/usr/bin/env python
"""
Add the verified open textbooks to the curated manifest.

This only edits catalogue/collection.json. It never touches the database: the
normal /api/admin/catalogue/seed endpoint applies the manifest, and the normal
import pipeline does the ingestion.

Each new entry records the exact edition, the official source and the licence
that was verified on the publisher's own page, including the non-commercial
and no-derivatives restrictions where they apply.
"""

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "catalogue", "collection.json")

LEVEL_ORDER = {"BEGINNER": 0, "INTERMEDIATE": 1, "ADVANCED": 2}

# --------------------------------------------------------------- new books
#
# resourceId, catalogue metadata and the staged file. A book is only listed
# here once its file is present under data/imports/incoming/.
NEW_BOOKS = [
    {
        "resourceId": "THINK-JAVA-2E-OEW",
        "title": "Think Java: How to Think Like a Computer Scientist (2nd Edition)",
        "author": "Allen B. Downey and Chris Mayfield",
        "category": "Programming",
        "genres": ["Programming", "Java"],
        "subjects": ["Java (Computer program language)", "Programming"],
        "description": (
            "A short, methodical introduction to Java for readers with no "
            "programming background. Variables, conditionals, loops, methods "
            "and objects are introduced one at a time, with exercises and "
            "worked examples after each idea."
        ),
        "language": "English",
        "publicationYear": 2020,
        "publisher": "Green Tea Press",
        "edition": "2nd edition, version 7.1.0",
        "sourceUrl": "https://greenteapress.com/wp/think-java-2e/",
        "licence": (
            "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-"
            "ShareAlike 4.0 International), as stated by Green Tea Press. "
            "Non-commercial use only; ShareAlike applies to derivatives."
        ),
        "file": "data/imports/incoming/think-java-2e.pdf",
    },
    {
        "resourceId": "JJJ-JAVA-3E-OEW",
        "title": "Java, Java, Java: Object-Oriented Problem Solving (3rd Edition)",
        "author": "Ralph Morelli and Ralph Walde",
        "category": "Programming",
        "genres": ["Programming", "Java"],
        "subjects": ["Java (Computer program language)", "Object-oriented programming"],
        "description": (
            "A full object-oriented Java course built around problem solving: "
            "classes and objects from the first chapter, then inheritance, "
            "interfaces, exceptions, threads, files and data structures, with "
            "a substantial design exercise in each part."
        ),
        "language": "English",
        "publicationYear": 2006,
        "publisher": "Trinity College (open edition)",
        "edition": "3rd edition, open-source PDF revised 2017-06-25",
        "sourceUrl": "https://www.cs.trincoll.edu/~ram/jjj/",
        "licence": (
            "CC BY 4.0 (Creative Commons Attribution 4.0 International), as "
            "stated by the authors. Redistribution permitted with attribution."
        ),
        "file": "data/imports/incoming/java-java-java-3e.pdf",
    },
    {
        "resourceId": "PYTHON-MASTERY-OEW",
        "title": "Advanced Python Mastery",
        "author": "David Beazley",
        "category": "Programming",
        "genres": ["Programming", "Python"],
        "subjects": ["Python (Computer program language)", "Metaprogramming"],
        "description": (
            "An advanced course on how Python actually works: the object "
            "model, descriptors, metaprogramming, generators and coroutines, "
            "decorators, context managers, packaging and performance. Written "
            "for programmers who already write Python daily."
        ),
        "language": "English",
        "publicationYear": 2023,
        "publisher": "Dabeaz LLC",
        "edition": "Course edition, Python 3",
        "sourceUrl": "https://github.com/dabeaz-course/python-mastery",
        "licence": (
            "CC BY-SA 4.0 (Creative Commons Attribution-ShareAlike 4.0 "
            "International), as stated in the course README."
        ),
        "file": "data/imports/incoming/advanced-python-mastery.pdf",
    },
    {
        "resourceId": "MODERN-C-OEW",
        "title": "Modern C (C23 Edition)",
        "author": "Jens Gustedt",
        "category": "Programming",
        "genres": ["Programming", "C"],
        "subjects": ["C (Computer program language)", "Systems programming"],
        "description": (
            "A complete treatment of C as it is written today: value "
            "semantics, pointer arithmetic done safely, the memory model, "
            "alignment, the preprocessor, generic programming with _Generic, "
            "threads and atomics, and the C23 additions."
        ),
        "language": "English",
        "publicationYear": 2024,
        "publisher": "Inria / HAL",
        "edition": "C23 edition, October 2024",
        "sourceUrl": "https://gustedt.gitlabpages.inria.fr/modern-c/",
        "licence": (
            "CC BY-NC-ND 4.0 (Creative Commons Attribution-NonCommercial-"
            "NoDerivatives 4.0 International), as stated by the author. "
            "Verbatim non-commercial use only."
        ),
        "file": "data/imports/incoming/modern-c-c23.pdf",
    },
    {
        "resourceId": "PROGRAMMING-FUNDAMENTALS-CPP-OEW",
        "title": "Programming Fundamentals: A Modular Structured Approach Using C++",
        "author": "Kenneth Leroy Busbee and Dave Braunschweig",
        "category": "Programming",
        "genres": ["Programming", "C++"],
        "subjects": ["C++ (Computer program language)", "Programming"],
        "description": (
            "An open introductory programming text that teaches program "
            "design before language detail: data types, operators, control "
            "structures, functions, arrays and modular design, with C++ used "
            "throughout for the worked examples."
        ),
        "language": "English",
        "publicationYear": 2019,
        "publisher": "Open Textbook Library / eCampusOntario",
        "edition": "2nd edition (eCampusOntario Open Library copy, 2019)",
        "sourceUrl": "https://open.umn.edu/opentextbooks/textbooks/144",
        "licence": (
            "CC BY-SA 4.0 (Creative Commons Attribution-ShareAlike 4.0 "
            "International), as stated on the copyright page of the "
            "eCampusOntario Open Library copy. Redistribution permitted "
            "under ShareAlike."
        ),
        "file": "data/imports/incoming/programming-fundamentals-cpp.pdf",
    },
    {
        "resourceId": "THINK-CPP-OEW",
        "title": "Think C++: How to Think Like a Computer Scientist",
        "author": "Allen B. Downey",
        "category": "Programming",
        "genres": ["Programming", "C++"],
        "subjects": ["C++ (Computer program language)", "Programming"],
        "description": (
            "A concise C++ text that moves from basic control flow to "
            "functions, classes, vectors, structures and object-oriented "
            "design, emphasising how to reason about a program rather than "
            "covering the whole language."
        ),
        "language": "English",
        "publicationYear": 1999,
        "publisher": "Green Tea Press",
        "edition": "Green Tea Press edition",
        "sourceUrl": "https://greenteapress.com/wp/think-c/",
        "licence": (
            "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-"
            "ShareAlike 4.0 International), as stated by Green Tea Press. "
            "Non-commercial use only."
        ),
        "file": "data/imports/incoming/think-cpp.pdf",
    },
    {
        "resourceId": "THE-JAVASCRIPT-WAY-OEW",
        "title": "The JavaScript Way",
        "author": "Baptiste Pesquet",
        "category": "Programming",
        "genres": ["Programming", "JavaScript"],
        "subjects": ["JavaScript (Computer program language)", "Web development"],
        "description": (
            "A modern JavaScript course: the language core, then objects, "
            "arrays and functions, then the browser, the DOM, events, forms, "
            "asynchronous programming with promises and a final project, with "
            "an exercise at the end of every chapter."
        ),
        "language": "English",
        "publicationYear": 2018,
        "publisher": "Self-published (open edition)",
        "edition": "Online edition, chapters 1-16 plus appendix",
        "sourceUrl": "https://thejsway.net/",
        "licence": (
            "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-"
            "ShareAlike 4.0 International), per the book's LICENSE file. "
            "Non-commercial use only."
        ),
        "file": "data/imports/incoming/the-javascript-way.html",
    },
    {
        "resourceId": "WIKIBOOKS-JAVA-OEW",
        "title": "Java Programming",
        "author": "Wikibooks contributors",
        "category": "Programming",
        "genres": ["Programming", "Java"],
        "subjects": ["Java (Computer program language)", "Programming"],
        "description": (
            "An openly licensed Java reference covering the advanced parts of "
            "the language and platform: generics, concurrent programming, "
            "reflection and dynamic class loading, bytecode, the native "
            "interface and remote method invocation."
        ),
        "language": "English",
        "publicationYear": 2026,
        "publisher": "Wikibooks",
        "edition": "Wikibooks live edition, chapter snapshot at download time",
        "sourceUrl": "https://en.wikibooks.org/wiki/Java_Programming",
        "licence": (
            "CC BY-SA 4.0 and GFDL (dual), per Wikibooks:Copyrights. "
            "Redistribution and commercial reuse permitted under ShareAlike."
        ),
        "file": "data/imports/incoming/wikibooks-java-programming.html",
    },
    {
        "resourceId": "WIKIBOOKS-C-OEW",
        "title": "C Programming",
        "author": "Wikibooks contributors",
        "category": "Programming",
        "genres": ["Programming", "C"],
        "subjects": ["C (Computer program language)", "Programming"],
        "description": (
            "An openly licensed C textbook that begins with the first program "
            "and works through variables, control flow, functions, pointers, "
            "arrays, strings, structures, memory management, the preprocessor "
            "and file input and output."
        ),
        "language": "English",
        "publicationYear": 2026,
        "publisher": "Wikibooks",
        "edition": "Wikibooks live edition, chapter snapshot at download time",
        "sourceUrl": "https://en.wikibooks.org/wiki/C_Programming",
        "licence": (
            "CC BY-SA 4.0 and GFDL (dual), per Wikibooks:Copyrights. "
            "Redistribution and commercial reuse permitted under ShareAlike."
        ),
        "file": "data/imports/incoming/wikibooks-c-programming.html",
    },
    {
        "resourceId": "THE-RUST-PROGRAMMING-LANGUAGE-OEW",
        "title": "The Rust Programming Language",
        "author": "Steve Klabnik, Carol Nichols and the Rust community",
        "category": "Programming",
        "genres": ["Programming", "Rust"],
        "subjects": ["Rust (Computer program language)", "Systems programming"],
        "description": (
            "The official Rust book: ownership and borrowing, structs, enums "
            "and pattern matching, error handling, generics, traits and "
            "lifetimes, then testing, closures, iterators, smart pointers, "
            "concurrency, async and a full project."
        ),
        "language": "English",
        "publicationYear": 2024,
        "publisher": "The Rust Project",
        "edition": "Current online edition (mdBook)",
        "sourceUrl": "https://doc.rust-lang.org/book/",
        "licence": (
            "MIT OR Apache-2.0, as stated in the rust-lang/book repository. "
            "Redistribution permitted."
        ),
        "file": "data/imports/incoming/the-rust-programming-language.html",
    },
]

# ------------------------------------------------- open-book pathway slots
#
# Where each new title sits in its language pathway, and why that level is
# justified by what the book actually contains.
OPEN_MEMBERS = {
    "java-programming": [
        {
            "resourceId": "WIKIBOOKS-JAVA-OEW",
            "level": "ADVANCED",
            "rationale": (
                "Goes past the language basics into generics, concurrent "
                "programming, reflection, bytecode and the native interface, "
                "which is the material an advanced reader needs."
            ),
            "learningOutcome": "Use generics and the concurrency utilities competently, and explain reflection and class loading in a running JVM.",
        },
        {
            "resourceId": "THINK-JAVA-2E-OEW",
            "level": "BEGINNER",
            "rationale": (
                "Starts from no programming experience and introduces one "
                "idea per chapter with exercises, which is why it precedes "
                "the reference-style beginner title."
            ),
            "learningOutcome": "Write, compile and run a Java program that uses variables, conditionals, loops, methods and simple objects.",
        },
        {
            "resourceId": "JJJ-JAVA-3E-OEW",
            "level": "INTERMEDIATE",
            "rationale": (
                "Assumes control flow and methods, then works through "
                "inheritance, interfaces, exceptions, threads and files as "
                "design problems rather than syntax tours."
            ),
            "learningOutcome": "Design a small class hierarchy, handle exceptions and write a multithreaded program deliberately.",
        },
    ],
    "python-programming": [
        {
            "resourceId": "PY4E-PYTHON-3",
            "level": "BEGINNER",
            "rationale": (
                "A complete first course in Python that assumes nothing and "
                "covers variables, loops, functions, files and data handling "
                "in order, with exercises throughout."
            ),
            "learningOutcome": "Write a Python program that reads data from a file, processes it and writes the result out.",
        },
        {
            "resourceId": "THINK-PYTHON-2E",
            "level": "INTERMEDIATE",
            "rationale": (
                "Takes over once the basics are secure and concentrates on "
                "program design: interfaces, invariants, recursion, objects "
                "and systematic debugging."
            ),
            "learningOutcome": "Decompose a problem into functions and classes and debug a failing program from its traceback.",
        },
        {
            "resourceId": "PYTHON-MASTERY-OEW",
            "level": "ADVANCED",
            "rationale": (
                "Begins where introductory texts stop: the object model, "
                "descriptors, generators, decorators and metaprogramming, "
                "assuming daily Python use."
            ),
            "learningOutcome": "Explain and use descriptors, decorators, generators and the object model to write library-quality Python.",
        }
    ],
    "c-programming": [
        {
            "resourceId": "WIKIBOOKS-C-OEW",
            "level": "BEGINNER",
            "rationale": (
                "Starts with the first program and introduces each construct "
                "with a worked example, so no prior C is assumed."
            ),
            "learningOutcome": "Compile and run a C program that uses variables, control flow, functions, arrays and structs.",
        },
        {
            "resourceId": "MODERN-C-OEW",
            "level": "INTERMEDIATE",
            "rationale": (
                "Covers the whole language with attention to pointer safety, "
                "the memory model and alignment, so it follows a first "
                "exposure rather than replacing it."
            ),
            "learningOutcome": "Write correct C that manages memory explicitly, uses the preprocessor deliberately and reasons about undefined behaviour.",
        }
    ],
    "cpp-programming": [
        {
            "resourceId": "PROGRAMMING-FUNDAMENTALS-CPP-OEW",
            "level": "BEGINNER",
            "rationale": (
                "Teaches program design before language detail and uses C++ "
                "for every worked example, so it needs no prior C++."
            ),
            "learningOutcome": "Structure a small program from problem statement to working code using functions, arrays and control structures.",
        },
        {
            "resourceId": "THINK-CPP-OEW",
            "level": "INTERMEDIATE",
            "rationale": (
                "Moves on to classes, vectors, structures and object-oriented "
                "design, assuming the control flow and functions covered by "
                "the beginner title."
            ),
            "learningOutcome": "Design and implement a small class hierarchy and use the standard containers idiomatically.",
        },
    ],
    "javascript-programming": [
        {
            "resourceId": "THE-JAVASCRIPT-WAY-OEW",
            "level": "INTERMEDIATE",
            "rationale": (
                "Assumes basic syntax and concentrates on the browser, the "
                "DOM, events, forms and asynchronous code, ending with a "
                "project."
            ),
            "learningOutcome": "Build an interactive browser application with event handling, form validation and asynchronous data loading.",
        }
    ],
    "rust-programming": [
        {
            "resourceId": "THE-RUST-PROGRAMMING-LANGUAGE-OEW",
            "level": "BEGINNER",
            "rationale": (
                "The official introduction: ownership and borrowing are "
                "explained from first principles before any advanced topic."
            ),
            "learningOutcome": "Write a Rust program that compiles under the borrow checker and uses structs, enums and error handling correctly.",
        }
    ],
}


def main():
    with open(MANIFEST, encoding="utf-8") as handle:
        manifest = json.load(handle)

    books = manifest["books"]
    known = {book["resourceId"] for book in books}

    added_books = 0

    for book in NEW_BOOKS:
        if book["resourceId"] in known:
            # Keep the manifest in step with a corrected author, edition or
            # licence statement rather than leaving a stale record behind.
            for index, existing in enumerate(books):
                if existing["resourceId"] == book["resourceId"]:
                    books[index] = book
                    break
            continue
        if not os.path.exists(os.path.join(ROOT, book["file"])):
            print("! missing file, not catalogued: %s" % book["file"])
            continue
        books.append(book)
        known.add(book["resourceId"])
        added_books += 1

    added_members = 0

    for pathway in manifest["pathways"]:

        open_slots = OPEN_MEMBERS.get(pathway["slug"])

        if not open_slots:
            continue

        present = {member["resourceId"] for member in pathway["books"]}

        for slot in open_slots:
            if slot["resourceId"] not in known:
                continue
            if slot["resourceId"] in present:
                continue
            pathway["books"].append(dict(slot))
            present.add(slot["resourceId"])
            added_members += 1

        # Sequence the pathway by level, readable open edition first within
        # each level, then renumber positions.
        open_ids = {slot["resourceId"] for slot in open_slots}

        ordered = sorted(
            pathway["books"],
            key=lambda member: (
                LEVEL_ORDER.get(member.get("level"), 9),
                0 if member["resourceId"] in open_ids else 1,
            ),
        )

        for position, member in enumerate(ordered, start=1):
            member["position"] = position

        pathway["books"] = ordered

    with open(MANIFEST, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    print(
        "books added: %d (total %d), pathway memberships added: %d"
        % (added_books, len(books), added_members)
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())
