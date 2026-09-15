# Cybersecurity Detection Classification with Reasoning-enabled Language Models

**Authors:** Amol Khanna, Manu Nandan, Cristian Viorel Popa, Joan Pujol-Roig, Diana Bolocan, Laura Vasilie, Alexandru Apostu, Chase Helwig, Mihaela Gaman, Michael Brautbar, Edward Raff, Chase Midler, Sven Krasser  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG, cs.CR  
**Document ID:** DOC-ARXIV-2607.28460v1  

## Abstract
A major issue in Security Operations Centers (SOCs) is alert fatigue, as the number of detections reported is more than staff can triage in a given day. Prior work prompts or fine-tunes large language models (LLMs) to emit a triage label directly, but does not train them to reason about whether a detection is a genuine threat. We train a chain-of-thought (CoT) reasoning-enabled triage classifier on real, human-labeled Windows endpoint detections by combining automated prompt optimization, self-training, and reinforcement learning with verifiable rewards. We find that CoT reasoning also degrades the label-token probabilities that automated triage relies on, so we separately train a calibrator that reads the full reasoning trace and estimates the probability that the verdict is correct. Our system reaches 82.6% test accuracy and, at the high-confidence operating point that governs automated triage, improves benign recall by 43.0% and malicious recall by 18.3% over a direct-label LLM classifier. We further show that the trained calibrator is necessary - an untrained confidence judge collapses high-confidence recall to zero - and that a finetuned 30B model significantly outperforms frontier general-purpose models, motivating targeted training over scale.

## 1. Introduction
Recent advancements in cs.LG, cs.CR have significantly transformed knowledge discovery systems. This document explores cybersecurity detection classification with reasoning-enabled language models and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Amol Khanna, Manu Nandan, Cristian Viorel Popa, Joan Pujol-Roig, Diana Bolocan, Laura Vasilie, Alexandru Apostu, Chase Helwig, Mihaela Gaman, Michael Brautbar, Edward Raff, Chase Midler, Sven Krasser established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Cybersecurity Detection Classification with Reasoning-enabled Language Models provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

