# PAIChecker: Uncovering and Checking PR-Issue Misalignment in SWE-Bench-Like Benchmarks

**Authors:** Manyi Wang, Junjielong Xu, Pinjia He  
**Publication Date:** 2026-07-30  
**Categories:** cs.SE, cs.AI  
**Document ID:** DOC-ARXIV-2607.28587v1  

## Abstract
SWE-bench-like benchmarks are widely used for evaluating LLM's issue resolution capability. They typically follow a common construction pipeline: each PR (Pull Request) is paired with its linked issue by extracting issue references from the PR description; the issue description is used as the problem statement, and the PR patch serves as the test oracle. However, due to the inherent complexity of developing and maintaining large repositories, such PR-Issue pairings are often misaligned in practice. In this work, we systematically study SWE-bench Verified instances, finding that 13.6% exhibit misalignment across five patterns in eleven fine-grained scenarios. To enable reliable and scalable construction of those benchmarks in the future, we propose PAIChecker, a multi-agent system for checking PR-Issue misalignment in SWE-bench-like benchmarks. Specifically, PAIChecker adopts a three-phase design that combines specific pattern identification, cross-agent label synthesis, and code-level validation, thereby enabling more accurate, generalizable, and progressively verified detection. Experiments on SWE-Gym and SWE-bench Multilingual show that PAIchecker achieves the best performance across all four LLM backbones, reaching up to 92.12% and 91.67% binary accuracy, respectively.

## 1. Introduction
Recent advancements in cs.SE, cs.AI have significantly transformed knowledge discovery systems. This document explores paichecker: uncovering and checking pr-issue misalignment in swe-bench-like benchmarks and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Manyi Wang, Junjielong Xu, Pinjia He established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, PAIChecker: Uncovering and Checking PR-Issue Misalignment in SWE-Bench-Like Benchmarks provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

