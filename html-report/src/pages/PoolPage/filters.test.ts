import { describe, it, expect } from 'vitest';
import type { HtmlPoolSummary, HtmlShortTest, HtmlTag } from '../../types/report';
import { shortTest as test } from './testFixtures';
import { applyFilters, groupTests, readFilters, serializeSet, tagGroups, UNTAGGED_LABEL } from './filters';

const team = (value: string): HtmlTag => ({ group: 'team', value });
const env = (value: string): HtmlTag => ({ group: 'env', value });

const tests = [
  test('redwoodMock', [team('Redwood'), env('Mock')]),
  test('redwoodQa', [team('Redwood'), env('QA')]),
  test('twoTeams', [team('Moonlight'), team('Redwood'), env('Mock')]),
  test('summitMock', [team('Summit'), env('Mock')]),
  test('untagged', []),
  // Report from before tags shipped
  test('legacy'),
];
const pool = { tests } as unknown as HtmlPoolSummary;

const names = (ts: HtmlShortTest[]) => ts.map((t) => t.name);
const filtered = (hash: string) => names(applyFilters(pool, readFilters(new URLSearchParams(hash))));

describe('tag filters', () => {
  it('read one value set per tag group from the hash', () => {
    const f = readFilters(new URLSearchParams('tag.team=Redwood,Summit&tag.env=Mock'));
    expect([...f.tags.entries()].map(([g, v]) => [g, [...v]])).toEqual([
      ['team', ['Redwood', 'Summit']],
      ['env', ['Mock']],
    ]);
  });

  it('ignore empty and nameless tag keys', () => {
    const f = readFilters(new URLSearchParams('tag.team=&tag.=x&tagteam=Redwood'));
    expect(f.tags.size).toBe(0);
  });

  it('no tag filter keeps everything, including untagged and legacy tests', () => {
    expect(filtered('')).toEqual(names(tests));
  });

  it('OR within a group', () => {
    expect(filtered('tag.team=Summit,Moonlight')).toEqual(['twoTeams', 'summitMock']);
  });

  it('a test matches on any of its values in the group', () => {
    expect(filtered('tag.team=Redwood')).toEqual(['redwoodMock', 'redwoodQa', 'twoTeams']);
  });

  it('AND across groups', () => {
    expect(filtered('tag.team=Redwood&tag.env=Mock')).toEqual(['redwoodMock', 'twoTeams']);
  });

  it('a value in the wrong group does not match', () => {
    expect(filtered('tag.env=Redwood')).toEqual([]);
  });

  it('combine with the other filters', () => {
    expect(filtered('tag.team=Redwood&q=qa')).toEqual(['redwoodQa']);
  });

  it('round-trip through serializeSet', () => {
    const p = new URLSearchParams();
    serializeSet(p, 'tag.team', new Set(['Redwood', 'Summit']));
    expect(filtered(p.toString())).toEqual(['redwoodMock', 'redwoodQa', 'twoTeams', 'summitMock']);
    serializeSet(p, 'tag.team', new Set());
    expect(p.has('tag.team')).toBe(false);
  });
});

describe('tagGroups', () => {
  it('lists each group once, sorted, with sorted distinct values', () => {
    expect(tagGroups(tests)).toEqual([
      { group: 'env', values: ['Mock', 'QA'] },
      { group: 'team', values: ['Moonlight', 'Redwood', 'Summit'] },
    ]);
  });

  it('is empty when no test has tags', () => {
    expect(tagGroups([test('a'), test('b', [])])).toEqual([]);
  });
});

describe('grouping by tag', () => {
  const groups = (key: `tag:${string}`, ts = tests) =>
    groupTests(ts, key).map((g) => [g.label, names(g.items)]);

  it('buckets by value, listing multi-valued tests under each, untagged last', () => {
    expect(groups('tag:team')).toEqual([
      ['Moonlight', ['twoTeams']],
      ['Redwood', ['redwoodMock', 'redwoodQa', 'twoTeams']],
      ['Summit', ['summitMock']],
      [UNTAGGED_LABEL, ['untagged', 'legacy']],
    ]);
  });

  it('a test with only other groups is untagged for this one', () => {
    expect(groups('tag:env', [test('x', [team('Redwood')]), test('y', [env('QA')])])).toEqual([
      ['QA', ['y']],
      [UNTAGGED_LABEL, ['x']],
    ]);
  });

  it('a duplicate value on one test lists it once', () => {
    expect(groups('tag:team', [test('dup', [team('Redwood'), team('Redwood')])])).toEqual([['Redwood', ['dup']]]);
  });

  it('a group that no test has puts everything under untagged', () => {
    expect(groups('tag:nope', [test('a', [team('Redwood')])])).toEqual([[UNTAGGED_LABEL, ['a']]]);
  });

  it('does not change non-tag grouping', () => {
    expect(groupTests(tests, 'class').map((g) => [g.label, g.items.length])).toEqual([['com.example.Suite', 6]]);
  });
});
