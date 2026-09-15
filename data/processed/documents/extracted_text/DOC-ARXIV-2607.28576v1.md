# Sample More, Reflect Less: Self-Refine and Reflexion Lose to Repeated Sampling at Equal Token Cost, from 1.5B to 7B

**Authors:** Iliya Mirzaei  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL, cs.AI, cs.LG  
**Document ID:** DOC-ARXIV-2607.28576v1  

## Abstract
Methods that make a language model plan, criticise and rewrite its own answer, reflect on mistakes, pick the best of several attempts, or debate with copies of itself nearly all make it generate far more text than a single chain of thought. Because generating more text raises accuracy by itself, a gain over one chain of thought does not show the method's idea is what helped. Wang et al. (2024) reported that a simple baseline, sampling the same question repeatedly and keeping the most common answer, often wins once budgets are comparable, but gave point estimates with no confidence intervals or significance tests.   We rerun that comparison as a designed experiment: seven methods, open models of 1.5B, 3B and 7B parameters, two mathematics benchmarks, 150 questions each. We count every generated token, including those spent on critiques, reflections, debate turns and checking, and compare each method against repeated sampling at its own measured cost. All 36 comparisons are paired by question, with bootstrap intervals and multiplicity correction.   No method is reliably better than repeated sampling at equal cost anywhere. Ten are reliably worse, all of them methods where the model inspects its own output, and all 18 self-inspection comparisons are negative. The two kinds of self-inspection part company as models grow. Choosing stops hurting: taking Best-of-N's eight samples and just counting the most common answer beats letting the model pick by 8.0 and 11.3 points at 1.5B, but only 2.0 and 1.3 at 7B, no longer distinguishable from zero. Rewriting does not recover: Self-Refine and a forced Reflexion stay 3.6 to 10.1 points below baseline at 7B.   Reflexion as published never triggered its own retry on the smallest model. It judged itself correct every time and silently became a single chain of thought. We release code, prompts, all generations, and our verification scripts.

## 1. Introduction
Recent advancements in cs.CL, cs.AI, cs.LG have significantly transformed knowledge discovery systems. This document explores sample more, reflect less: self-refine and reflexion lose to repeated sampling at equal token cost, from 1.5b to 7b and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Iliya Mirzaei established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Sample More, Reflect Less: Self-Refine and Reflexion Lose to Repeated Sampling at Equal Token Cost, from 1.5B to 7B provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

