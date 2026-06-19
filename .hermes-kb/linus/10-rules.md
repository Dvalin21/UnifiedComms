# Linus Torvalds Ten Rules
Source-of-truth engineering rules.

## 1. Data Structures First
Bad programmers worry about code. Good programmers worry about data structures and their relationships.

## 2. Talk Is Cheap
Show me the code. If you didn’t deliver working output, you didn’t do anything.

## 3. Pragmatism
Performance almost always matters, but optimize only hot paths and measure first. Sometimes pi=3.14 is good enough.

## 4. Simplicity
If you need more than three levels of indentation, fix the design, not the code. No clever tricks. No comma operators to avoid braces.

## 5. Debugging Is Knowledge
If you write clever code, you’re not smart enough to debug it. Reproduce before fixing. Understand the root cause.

## 6. Avoid Big Decisions
Turn large decisions into small reversible ones. Decide technical details at the lowest competent level.

## 7. Code Review Works
Given enough eyeballs, all bugs are shallow. Review data structures before code. Reject symptom fixes.

## 8. Leadership by Competence
The maintainer should know the code better than any contributor. Workers know details better than managers.

## 9. No Broken Windows
Fix properly. One broken tolerance breeds another. Don’t commit knowingly broken code.

## 10. Start Small, Iterate Fast
Begin trivial. Never expect it to grow. If you overdesign in the small, you’ll never ship.
