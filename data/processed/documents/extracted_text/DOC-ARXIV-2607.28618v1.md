# AskChem: Claim-Centered Infrastructure for Chemistry Literature Synthesis

**Authors:** Bing Yan, Gregory Wolfe, Stefano Martiniani, Kyunghyun Cho  
**Publication Date:** 2026-07-30  
**Categories:** cs.CL, cs.AI, cs.IR, cs.LG  
**Document ID:** DOC-ARXIV-2607.28618v1  

## Abstract
Chemistry literature synthesis often requires assembling specific findings scattered across many publications, yet existing literature-search systems primarily return ranked document lists. As a result, scientists and AI agents need to locate relevant information, verify their provenance, and assemble cross-paper answers manually. We present AskChem, a claim-centered infrastructure for cross-paper chemistry search. AskChem changes the unit of retrieval from the paper to the provenance-carrying claim: each paper is converted into atomic, typed claims, each grounded by a source DOI and a verbatim quote or an explicit evidence locator. Over this shared claim store, AskChem exposes complementary structures for search and synthesis: a stabilized faceted taxonomy for hierarchical retrieval and browsing, an evidence graph linking claims through relations, and an exploratory living taxonomy that situates indexed papers under scientific principles. AskChem currently indexes 2.4M claims from 147K papers and provides a web interface, as well as REST, SDK, and MCP access for AI agents. On AskChem-Bench, grounding a GPT-5.5 reader in AskChem yields 100% resolvable DOIs, compared with 88.3% without retrieval, and the highest citation density among five tested systems. AskChem is live at https://askchem.org.

## 1. Introduction
Recent advancements in cs.CL, cs.AI, cs.IR, cs.LG have significantly transformed knowledge discovery systems. This document explores askchem: claim-centered infrastructure for chemistry literature synthesis and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Bing Yan, Gregory Wolfe, Stefano Martiniani, Kyunghyun Cho established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, AskChem: Claim-Centered Infrastructure for Chemistry Literature Synthesis provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

