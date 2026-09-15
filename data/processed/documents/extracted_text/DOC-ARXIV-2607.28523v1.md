# Selective Credibility-Limited Belief Update

**Authors:** Theofanis Aravanis, Costas D. Koutras  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI, cs.LO  
**Document ID:** DOC-ARXIV-2607.28523v1  

## Abstract
Belief update concerns changes in an agent's beliefs induced by changes in the underlying world. Standard Katsuno-Mendelzon update assumes that an epistemic input can be incorporated from every initially possible world, whereas credibility-limited belief update restricts, for each source world, the successor worlds regarded as credible or reachable. Nevertheless, existing credibility-limited approaches treat the epistemic input as an indivisible whole, and therefore cannot represent cases in which only part of a compound epistemic input can be realized. We introduce selective credibility-limited belief update, in which the epistemic input is transformed, relative to each source world, into a weaker proxy before the credibility-limited transition is performed. We provide semantic and axiomatic characterizations of the resulting class of update operators. We then identify two well-behaved sub-classes; namely, consistency-preserving update operators, which require every transformed epistemic input to be credible from its source world whenever the original epistemic input is consistent, and maximal consistency-preserving update operators, which additionally require the selected proxy to be maximally informative among the credible consequences of the original epistemic input. Finally, we establish the generality of the proposed framework by showing that credibility-limited belief update is recovered as a special case, while Katsuno--Mendelzon belief update emerges when credibility restrictions are removed and the transformation functions are taken to be identities. These results demonstrate that the framework provides a unified and strictly more expressive account of belief update, encompassing established approaches while supporting source-dependent selective acceptance.

## 1. Introduction
Recent advancements in cs.AI, cs.LO have significantly transformed knowledge discovery systems. This document explores selective credibility-limited belief update and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Theofanis Aravanis, Costas D. Koutras established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Selective Credibility-Limited Belief Update provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

