#!/usr/bin/env python3
"""
Merges the resolved programming-library data into catalogue/collection.json.

Editorial rules encoded here:

  * these are commercial, in-copyright books, so every entry is
    METADATA_ONLY: we hold a bibliographic record and a cover, never the
    text, and the manifest says so in the licence field;
  * Eloquent JavaScript already exists in the collection with a genuine
    CC-licensed EPUB, so the JavaScript pathway reuses that record
    instead of creating a second catalogue entry for the same book;
  * each language gets one LEARNING pathway with exactly three books,
    one per level, and a prerequisite chain;
  * entries are idempotent by resourceId, so re-applying never
    duplicates a book.
"""

import json
import os
import re

HERE = os.path.dirname(__file__)
MANIFEST = os.path.join(HERE, "collection.json")
SOURCE = os.path.join(HERE, "programming-books.json")

# resourceId suffix per language, used to build stable manifest ids.
SLUGS = {
    "Python": "python",
    "Java": "java",
    "C": "c",
    "C++": "cpp",
    "JavaScript": "javascript",
    "C#": "csharp",
    "Go": "go",
    "Rust": "rust",
}

SUBJECTS = {
    "Python": "Python (Computer program language)",
    "Java": "Java (Computer program language)",
    "C": "C (Computer program language)",
    "C++": "C++ (Computer program language)",
    "JavaScript": "JavaScript (Computer program language)",
    "C#": "C# (Computer program language)",
    "Go": "Go (Computer program language)",
    "Rust": "Rust (Computer program language)",
}

PATHWAY_COPY = {
    "Python": (
        "Python from first scripts to expert-level language internals.",
        "Write clear Python, then understand how the language's data model and concurrency actually behave.",
    ),
    "Java": (
        "Java from language basics to API and object-design judgement.",
        "Design Java APIs that remain correct and maintainable as a codebase grows.",
    ),
    "C": (
        "C from syntax and memory basics to the gaps between C and real implementations.",
        "Explain undefined behaviour, linkage and memory layout in production C code.",
    ),
    "C++": (
        "Modern C++ from guided fundamentals to effective use of the language and its standard library.",
        "Use modern C++ facilities correctly, and know when the straightforward idiom is the right one.",
    ),
    "JavaScript": (
        "JavaScript from fundamentals through the language's sharp edges to the complete reference.",
        "Read and write JavaScript whose scoping, coercion and async behaviour you can predict.",
    ),
    "C#": (
        "C# from a first project to the language's type-system and runtime details.",
        "Use C# generics, LINQ and async correctly, and understand what the runtime does underneath.",
    ),
    "Go": (
        "Go from small programs to the language's concurrency model.",
        "Write Go programs whose goroutines, channels and error handling stay understandable.",
    ),
    "Rust": (
        "Rust from ownership basics to advanced trait and async design.",
        "Build Rust programs that satisfy the borrow checker by design rather than by fighting it.",
    ),
}

# Descriptions and rationales are written for this catalogue; no publisher
# copy is reproduced.
DESCRIPTIONS = {
    "Python Crash Course":
        "A project-driven introduction to Python: variables, lists, classes and files, followed by "
        "three worked projects in game programming, data visualisation and web applications.",
    "Effective Python":
        "Ninety specific, tested practices for writing Python that is easier to read, faster to run "
        "and less surprising, each with the reason it works.",
    "Fluent Python":
        "A deep tour of the Python data model: sequences, dictionaries, closures, objects, "
        "generators, coroutines and concurrency, for programmers who already write Python.",
    "Head First Java":
        "A visual, exercise-heavy introduction to Java covering objects, inheritance, interfaces, "
        "collections, threads and basic GUI work.",
    "Core Java, Volume I":
        "The first volume of the standard Java reference, covering the language from syntax and "
        "objects through generics, collections, concurrency and I/O.",
    "Effective Java":
        "Ninety language-level recommendations for writing Java that is correct, clear and "
        "maintainable, with the reasoning and the counter-examples.",
    "Let Us C":
        "An exercise-rich introduction to C that starts from variables and control flow and works "
        "up to pointers, structures, files and the preprocessor.",
    "C Programming: A Modern Approach":
        "A complete C textbook that explains the language against the C standard, including "
        "pointers, the preprocessor, library use and the reasons behind each rule.",
    "Expert C Programming":
        "A book about the parts of C that textbooks leave out: undeclared behaviour, memory "
        "layout, linkage, arrays versus pointers and the compiler's view of a program.",
    "Programming: Principles and Practice Using C++":
        "Stroustrup's introduction to programming itself, using C++ to teach program design, "
        "types, classes, testing and error handling rather than only syntax.",
    "C++ Primer":
        "A thorough introduction to modern C++ that teaches the language as it is written today, "
        "from types and classes to the standard library, templates and special-purpose tools.",
    "Effective Modern C++":
        "Forty-two specific items on C++11 and C++14: type deduction, move semantics, smart "
        "pointers, lambdas and concurrency, with the reasoning behind each guideline.",
    "Eloquent JavaScript":
        "A modern introduction to JavaScript and programming, from values and control flow to the "
        "browser, the DOM, asynchronous code and Node.js, with end-of-chapter projects.",
    "You Don't Know JS Yet":
        "A close look at the parts of JavaScript that programmers commonly guess at: scope, "
        "closures, types, coercion, this-binding and prototype behaviour.",
    "JavaScript: The Definitive Guide":
        "The long-standing reference and tutorial for JavaScript, covering the language, the "
        "browser APIs, Node.js and the standard library in depth.",
    "Head First C#":
        "A visual, project-based introduction to C# and .NET that builds a working application "
        "while introducing types, objects, LINQ, events and file access.",
    "The C# Player's Guide":
        "A problem-first C# course that starts with a single line of code and builds up through "
        "objects, collections, generics, LINQ and the .NET libraries.",
    "C# in Depth":
        "An explanation of why C# behaves as it does: types, generics, delegates, LINQ, async, "
        "patterns and the runtime features behind them.",
    "Get Programming with Go":
        "A short-lesson introduction to Go for newcomers, building command-line programs, then "
        "covering structs, interfaces, concurrency and basic web services.",
    "The Go Programming Language":
        "The authoritative Go text from two of its designers, covering syntax, types, interfaces, "
        "goroutines, channels, testing and reflection with worked programs.",
    "Concurrency in Go":
        "A guide to Go's concurrency model in practice: goroutines, channels, select, "
        "cancellation, race conditions and the patterns that keep concurrent code correct.",
    "The Rust Programming Language":
        "The official Rust book: ownership, borrowing, lifetimes, traits, generics, error "
        "handling, testing and concurrency, with a substantial worked project.",
    "Rust in Action":
        "A systems-programming introduction to Rust that builds real programs over files, "
        "networks, memory-mapped data and the operating system.",
    "Rust for Rustaceans":
        "A follow-on book for working Rust programmers covering types, traits, generics, "
        "lifetimes, async, unsafe code and the design pressure behind each feature.",
}

RATIONALE = {
    "BEGINNER":
        "Assumes no prior experience with the language and introduces its syntax and core ideas "
        "through worked examples.",
    "INTERMEDIATE":
        "Assumes the language's basics and concentrates on using it well in real programs.",
    "ADVANCED":
        "Assumes working fluency and addresses language internals, edge cases and expert judgement.",
}

LEARNING_OUTCOME = {
    "Python": {
        "BEGINNER": "Write and run Python programs that use functions, collections and files.",
        "INTERMEDIATE": "Apply idiomatic Python practices in code that others have to maintain.",
        "ADVANCED": "Explain and use the Python data model, generators and concurrency features.",
    },
    "Java": {
        "BEGINNER": "Write Java classes that use inheritance, interfaces and collections.",
        "INTERMEDIATE": "Use the core Java libraries effectively, including generics and concurrency.",
        "ADVANCED": "Design Java APIs and classes that stay correct as the codebase grows.",
    },
    "C": {
        "BEGINNER": "Write C programs that use control flow, functions, arrays and pointers safely.",
        "INTERMEDIATE": "Structure larger C programs correctly around the C standard library.",
        "ADVANCED": "Diagnose undefined behaviour, linkage and memory-layout problems in C.",
    },
    "C++": {
        "BEGINNER": "Write C++ programs that use classes, containers and exceptions correctly.",
        "INTERMEDIATE": "Use modern C++ and the standard library to build well-typed programs.",
        "ADVANCED": "Choose between modern C++ alternatives with the reasoning behind each.",
    },
    "JavaScript": {
        "BEGINNER": "Write JavaScript that manipulates the DOM and handles asynchronous work.",
        "INTERMEDIATE": "Predict scope, coercion and this-binding instead of guessing at them.",
        "ADVANCED": "Navigate the full language and host APIs from the reference.",
    },
    "C#": {
        "BEGINNER": "Build a small C# application using classes, collections and events.",
        "INTERMEDIATE": "Use generics, LINQ and the .NET libraries to solve practical problems.",
        "ADVANCED": "Explain how C# generics, async and pattern matching behave at runtime.",
    },
    "Go": {
        "BEGINNER": "Write Go programs that use structs, interfaces and basic concurrency.",
        "INTERMEDIATE": "Build idiomatic Go services with goroutines, channels and tests.",
        "ADVANCED": "Keep concurrent Go code correct under cancellation, failure and load.",
    },
    "Rust": {
        "BEGINNER": "Write Rust programs that satisfy ownership and borrowing rules.",
        "INTERMEDIATE": "Build Rust programs that interact with files, networks and the OS.",
        "ADVANCED": "Design Rust APIs using traits, lifetimes and async without fighting the compiler.",
    },
}

# Eloquent JavaScript is already in the collection as a licensed EPUB.
REUSE = {"Eloquent JavaScript": "ELOQUENTJS-3E"}


def resource_id(language, level):
    return f"PROG-{SLUGS[language].upper()}-{level[:3]}"


def main():
    with open(SOURCE, encoding="utf-8") as handle:
        resolved = json.load(handle)

    with open(MANIFEST, encoding="utf-8") as handle:
        manifest = json.load(handle)

    existing = {book.get("resourceId") for book in manifest["books"]}
    by_title = {book["title"]: book for book in manifest["books"]}

    added = []
    pathways = {pathway["slug"]: pathway for pathway in manifest["pathways"]}

    for book in resolved:
        language = book["language"]
        title = book["title"]

        if title in REUSE:
            ref = REUSE[title]
            assert ref in existing, f"{ref} should already exist in the manifest"
            continue

        ref = resource_id(language, book["level"])

        if book["title"] == "Core Java, Volume I":
            title = "Core Java, Volume I: Fundamentals"

        entry = {
            "resourceId": ref,
            "title": title,
            "author": book["author"],
            "category": "Programming",
            "genres": ["Programming", language],
            "subjects": [SUBJECTS[language], "Programming"],
            "description": DESCRIPTIONS.get(
                book["title"], "A programming book recorded in the AstraLib catalogue."
            ),
            "language": "English",
            "availability": "METADATA_ONLY",
            "licence": (
                "In copyright. Bibliographic record and cover only: AstraLib holds no "
                "licensed digital edition of this title."
            ),
        }

        for source_field, target in (
            ("publicationYear", "publicationYear"),
            ("publisher", "publisher"),
            ("isbn", "isbn"),
            ("coverImage", "coverImage"),
            ("openLibraryWork", "sourceUrl"),
        ):
            value = book.get(source_field)
            if value:
                entry[target] = (
                    f"https://openlibrary.org{value}" if target == "sourceUrl" else value
                )

        if entry["title"] == "Core Java, Volume I: Fundamentals":
            pass

        if ref in existing:
            # Keep one record: update the manifest entry in place.
            manifest["books"] = [
                entry if b.get("resourceId") == ref else b for b in manifest["books"]
            ]
            continue

        manifest["books"].append(entry)
        existing.add(ref)
        added.append(entry)

    # ---------------------------------------------------------------
    # PATHWAYS
    # ---------------------------------------------------------------

    for language, slug in SLUGS.items():
        pathway_slug = f"{slug}-programming"
        description, _ = PATHWAY_COPY[language]

        levels = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]
        members = []

        for position, level in enumerate(levels, start=1):
            match = next(
                b for b in resolved if b["language"] == language and b["level"] == level
            )
            ref = REUSE.get(match["title"], resource_id(language, level))

            member = {
                "resourceId": ref,
                "position": position,
                "level": level,
                "rationale": RATIONALE[level],
                "learningOutcome": LEARNING_OUTCOME[language][level],
            }

            if position > 1:
                previous = next(
                    b for b in resolved
                    if b["language"] == language and b["level"] == levels[position - 2]
                )
                member["prerequisite"] = REUSE.get(
                    previous["title"], resource_id(language, levels[position - 2])
                )

            members.append(member)

        pathway = {
            "slug": pathway_slug,
            "name": f"{language if language != 'C++' else 'C++'} Programming",
            "type": "LEARNING",
            "subject": f"Programming in {language}",
            "audience": (
                f"Readers learning {language}, from their first program to expert-level work."
            ),
            "description": description,
            "books": members,
        }

        if pathway_slug in pathways:
            manifest["pathways"] = [
                pathway if p["slug"] == pathway_slug else p for p in manifest["pathways"]
            ]
        else:
            manifest["pathways"].append(pathway)

    with open(MANIFEST, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    print(f"Books in manifest: {len(manifest['books'])} (new: {len(added)})")
    print(f"Pathways in manifest: {len(manifest['pathways'])}")
    print("New titles:")
    for entry in added:
        print(
            f"  {entry['resourceId']:<22} {entry.get('publicationYear')} "
            f"{entry['title']} — {entry['author']}"
        )


if __name__ == "__main__":
    main()
