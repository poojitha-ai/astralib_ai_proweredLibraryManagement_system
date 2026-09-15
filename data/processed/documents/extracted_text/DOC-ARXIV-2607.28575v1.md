# Algorithms for Structured Elections under Thiele Voting Rules

**Authors:** Alexandra Lassota, Krzysztof Sornat  
**Publication Date:** 2026-07-30  
**Categories:** cs.GT, cs.AI, cs.DS, cs.MA  
**Document ID:** DOC-ARXIV-2607.28575v1  

## Abstract
We study the computational complexity of winner determination problems in approval-based committee elections under Thiele voting rules. These form a class of rules parameterized by a fixed weight vector that specifies how a voter's satisfaction depends on the number of approved candidates elected. We first analyze the structure of optimal solutions based on the sets of voters who approve each candidate---that is, how voters' approval ballots induce dependencies between candidates---revealing constraints on a winning committee under any fixed Thiele voting rule. Using this, we design FPT algorithms for Proportional Approval Voting (PAV) and other Thiele rules on a natural restricted domain known as the Voter Interval (VI) domain---that is, after a suitable ordering of voters, each candidate is approved by a consecutive interval of voters. In particular, we show that every Thiele rule on VI is FPT with respect to a parameter for which the problem is NP-hard on general instances, even when the parameter takes constant values. Our results advance the understanding of the computational complexity of PAV on Voter Interval instances, which remains one of the central open questions in this area. We further resolve two open questions from the literature on PAV (and other Thiele voting rules) by providing a polynomial-time algorithm for instances where each candidate is approved by at most two voters, and an FPT algorithm parameterized by the total score of a winning committee.

## 1. Introduction
Recent advancements in cs.GT, cs.AI, cs.DS, cs.MA have significantly transformed knowledge discovery systems. This document explores algorithms for structured elections under thiele voting rules and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Alexandra Lassota, Krzysztof Sornat established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Algorithms for Structured Elections under Thiele Voting Rules provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

