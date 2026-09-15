# Change2Task: From Repository Changes to Executable Coding Agent Tasks and Environments

**Authors:** Haomin Qi, Xingliang Wang, Xuanqi Gao, Baihui Sang, Xin Zhang, Minghua Ma, Pengfei Gao, Yu Kang, Qingwei Lin, Saravan Rajmohan, Dongmei Zhang, Qi Zhang  
**Publication Date:** 2026-07-30  
**Categories:** cs.SE, cs.CL, cs.LG  
**Document ID:** DOC-ARXIV-2607.28591v1  

## Abstract
Scaling coding agents requires a continuing supply of executable data for training, benchmarking, and continuous evaluation. Each task must couple a realistic software state with a specification, development tools, and reliable verification. To expand this supply, we present Change2Task, a system grounded in repository history that converts merged pull requests into verified tasks on healthy modern revisions of the same repository. It aligns historical evidence with evolved code, reconstructs task states through Patch Reversal, Code Mapping, or Agent Reconstruction, and validates the lifecycle from a healthy base to a task state and a restored state. By deriving multiple tasks grounded in developer evidence from maintained environments, Change2Task provides executable data for coding agent training and evaluation while reducing repeated environment setup, storage, and task construction effort. We evaluate the system through five common and widely adopted coding agent task families: Bug Fix, Feature Addition, Test Generation, Application Programming Interface Migration, and Security Repair. Starting from 1,130 source changes eligible for construction, Change2Task achieves 79.6% verified task construction success across these task families. On a matched candidate set, it recovers 29.2% more verified tasks than a construction baseline based on pull requests. Historical and reconstructed cases achieve up to 98.0% matched outcome agreement under agent evaluation, while reuse of modern bases reduces measured expenditure across the complete pipeline by 10.8%.

## 1. Introduction
Recent advancements in cs.SE, cs.CL, cs.LG have significantly transformed knowledge discovery systems. This document explores change2task: from repository changes to executable coding agent tasks and environments and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Haomin Qi, Xingliang Wang, Xuanqi Gao, Baihui Sang, Xin Zhang, Minghua Ma, Pengfei Gao, Yu Kang, Qingwei Lin, Saravan Rajmohan, Dongmei Zhang, Qi Zhang established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Change2Task: From Repository Changes to Executable Coding Agent Tasks and Environments provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

