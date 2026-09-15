# A foundation model of numerical intelligence with cross-disciplinary generalization

**Authors:** Chenghan Wu, Zongmin Yu, Liu Yang  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28432v1  

## Abstract
Intelligence is commonly understood as the ability to acquire and apply knowledge, adapt to unfamiliar situations and solve new problems. Large language models exhibit this capacity by inferring task-relevant knowledge from textual context and applying it to new tasks. Yet intelligence need not be confined to language. For scientific and social systems, we need models that acquire and apply knowledge from numerical context-an ability we call numerical intelligence. Here we introduce UNified In-Context Operator Networks (UNICON), a foundation model that exhibits numerical intelligence across disciplines. Using graph-based examples from a system as context, UNICON infers the predictive relation shared across them and applies it to queries from the same system. Across scientific and social systems, including those from disciplines absent from training, the same model approaches specialist performance without retraining. Combining UNICON with language-model agents yields further gains, enabling it to surpass state-of-the-art specialists in a discipline unseen in training. We further show that training-corpus diversity improves generalization to unseen disciplines. Together, these results establish UNICON as a foundation model of numerical intelligence and position it as a building block for a broader ecosystem of artificial intelligence.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores a foundation model of numerical intelligence with cross-disciplinary generalization and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Chenghan Wu, Zongmin Yu, Liu Yang established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, A foundation model of numerical intelligence with cross-disciplinary generalization provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

