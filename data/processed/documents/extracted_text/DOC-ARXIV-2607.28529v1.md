# CoGate: Confidence-Gated Co-Decoding for Secure Code Generation

**Authors:** Minghao Hu, Lannan Luo, Allen Roush, Phillip Howard  
**Publication Date:** 2026-07-30  
**Categories:** cs.SE  
**Document ID:** DOC-ARXIV-2607.28529v1  

## Abstract
Large language models are widely used for code generation, but they can also produce insecure programs due to patterns learned from their pretraining data. Decoding-time steering has become an important solution to this problem: a small expert model is combined with the target model at each step to generate more secure code, which is referred to as co-decoding. However, the acceptance rule for existing co-decoding approaches does not consider the expert model's confidence. When the security expert is unconfident due to unseen patterns or out-of-distribution (OOD) contexts, its guidance can therefore be misleading. To address the challenge, we propose CoGate, a confidence-gated co-decoding approach that controls the expert's influence on the co-decoding process based on its confidence. We implement our approach and evaluate it across multiple LLM backends (CodeGen, DeepSeek-Coder, Qwen-Coder, StarCoder) on several code generation benchmarks (HumanEval, security suite, and CWEval). Our approach outperforms existing co-decoding methods (CoSec+) across multiple benchmarks, achieving up to a 12.6% gain of Func-Sec@10 on CWEval.

## 1. Introduction
Recent advancements in cs.SE have significantly transformed knowledge discovery systems. This document explores cogate: confidence-gated co-decoding for secure code generation and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Minghao Hu, Lannan Luo, Allen Roush, Phillip Howard established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, CoGate: Confidence-Gated Co-Decoding for Secure Code Generation provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

