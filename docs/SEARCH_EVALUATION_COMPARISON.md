# Smart Search — before and after, same query set

`before` is the unchanged semantic and hybrid endpoints (fixed 0.60 cosine floor, fixed hybrid weights). `after` is `/api/search/smart`. Both reports were produced by `catalogue/evaluate_search.py` on the same 32 queries.

| Query | Before (best of semantic / hybrid) | After (smart) | Before rank | After rank | Intent |
| --- | --- | --- | --- | --- | --- |
| I'm new to programming, which Python book should I start with? | hit | hit | 1 | 1 | RECOMMENDATION |
| Something easy for a complete beginner in Java | hit | hit | 2 | 1 | RECOMMENDATION |
| Best book to learn C++ from scratch? | hit | hit | 1 | 1 | RECOMMENDATION |
| I already know the basics, recommend a more advanced Rust book | hit | hit | 1 | 1 | RECOMMENDATION |
| Easy introduction to JavaScript for someone who only knows HTML | hit | hit | 1 | 1 | RECOMMENDATION |
| What should I read after learning basic C? | hit | hit | 1 | 1 | RECOMMENDATION |
| Which book teaches advanced Python metaprogramming and decorators? | hit | hit | 1 | 1 | RECOMMENDATION |
| I want a beginner friendly book about object oriented programming | **miss** | hit | — | 2 | RECOMMENDATION |
| How does a while loop work? | hit | hit | 1 | 1 | CONCEPT |
| What is inheritance in object-oriented programming? | hit | hit | 3 | 2 | CONCEPT |
| How do I declare a pointer in C and take the address of a variable? | hit | hit | 1 | 1 | PROCEDURAL |
| What is a vector in C++? | hit | hit | 1 | 1 | CONCEPT |
| How do I define a Python function with default arguments? | **miss** | hit | — | 2 | PROCEDURAL |
| What is a closure in JavaScript? | **miss** | hit | — | 1 | CONCEPT |
| How does Rust ownership and borrowing work? | hit | hit | 1 | 1 | CONCEPT |
| Explain stacks and queues | **miss** | hit | — | 1 | CONCEPT |
| What is recursion and when should I use it? | **miss** | hit | — | 2 | CONCEPT |
| How is a linked list stored in memory? | **miss** | hit | — | 1 | CONCEPT |
| How does virtual memory paging work? | hit | hit | 1 | 1 | CONCEPT |
| What is a deadlock in an operating system? | hit | hit | 1 | 1 | CONCEPT |
| How does the scheduler decide which process runs next? | hit | hit | 1 | 1 | CONCEPT |
| How does TCP establish a connection? | **miss** | hit | — | 1 | CONCEPT |
| What is supervised learning? | correct | **miss** | — | — | CONCEPT |
| Explain gradient descent | correct | **miss** | — | — | CONCEPT |
| What is the difference between precision and recall? | correct | **miss** | — | — | CONCEPT |
| Why does Elizabeth Bennet dislike Mr Darcy? | hit | hit | 1 | 1 | CONCEPT |
| How does Holmes solve the case of the hound? | hit | hit | 1 | 1 | CONCEPT |
| What does Sun Tzu say about deception in war? | hit | hit | 1 | 1 | CONCEPT |
| I keep forgetting how memory works in C | hit | hit | 1 | 1 | OTHER |
| Which C# book should I start with? | **miss** | hit | — | 1 | RECOMMENDATION |
| What is the difference between a process and a thread? | hit | hit | 1 | 1 | CONCEPT |
| Why do my C++ objects get copied when I pass them to a function? | **miss** | hit | — | 1 | CONCEPT |

## Summary

| Run | Top-3 correct | Accuracy | Empty results | Stub sources | Median latency |
| --- | --- | --- | --- | --- | --- |
| before (best existing engine) | 23/32 | 72% | 9 | 0 | 1507 ms |
| after (smart) | 31/32 | 97% | 1 | 0 | 2327 ms |
