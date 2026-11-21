import { strict as assert } from "node:assert";
import { readFileSync, writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const [source, prompts, target] = process.argv.slice(2);
const { buildCoveragePlan } = await import(pathToFileURL(prompts).href);
const tokens = JSON.parse(readFileSync(source, "utf8"));
const full = buildCoveragePlan(tokens, { unit: "lemma", maxPhrase: 1 });
const plan = {
  ...full,
  cards: full.cards.map(({ book, greek, gloss, occurrences }) => ({ book, greek, gloss, occurrences }))
};
const books = new Set([
  "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH",
  "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS",
  "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV"
]);
assert.equal(tokens.length, 137741);
assert.equal(plan.summary.tokens, 133115);
assert.equal(plan.summary.cards, 4917);
assert.equal(plan.summary.coverage, 1);
assert.equal(plan.cards.length, plan.summary.cards);
assert.equal(new Set(plan.cards.map((card) => card.greek)).size, plan.cards.length);
assert.equal(plan.stages[0].book, "JHN");
assert.deepEqual(new Set(plan.stages.map((stage) => stage.book)), books);
assert.ok(plan.cards.every((card) =>
  books.has(card.book) && card.greek && typeof card.gloss === "string" && Number.isInteger(card.occurrences)
));
writeFileSync(target, `${JSON.stringify(plan)}\n`);
console.log(`Generated ${plan.cards.length} cards from ${tokens.length} XML words.`);
