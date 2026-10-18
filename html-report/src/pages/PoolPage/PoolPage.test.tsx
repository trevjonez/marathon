import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { render as rtlRender, screen, fireEvent, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import { ThemeProvider } from '../../theme/ThemeProvider';
import { PoolPage } from './PoolPage';
import type { HtmlPoolSummary, HtmlTag } from '../../types/report';
import { device, shortTest } from './testFixtures';

beforeAll(() => {
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
  window.matchMedia ??= ((query: string) => ({
    matches: false,
    media: query,
    addEventListener() {},
    removeEventListener() {},
  })) as unknown as typeof window.matchMedia;
  window.scrollTo = () => {};
});

const render = (ui: ReactElement) => rtlRender(<ThemeProvider>{ui}</ThemeProvider>);

beforeEach(() => {
  history.replaceState(null, '', '#');
});

const team = (value: string): HtmlTag => ({ group: 'team', value });

function pool(tests = [
  shortTest('redwood', [team('Redwood'), { group: 'env', value: 'Mock' }]),
  shortTest('summit', [team('Summit')]),
  shortTest('legacy'),
]): HtmlPoolSummary {
  return {
    id: 'omni',
    tests,
    passed_count: tests.length,
    failed_count: 0,
    ignored_count: 0,
    flaky_count: 0,
    duration_millis: 0,
    start_time_ms: 0,
    end_time_ms: 0,
    wallclock_duration_millis: 0,
    flake_overhead_millis: 0,
    devices: [device],
  };
}

const category = (label: string) => screen.getByText(`${label}:`).parentElement!;

describe('PoolPage tags', () => {
  it('shows one filter category per tag group with its values', () => {
    render(<PoolPage data={pool()} />);
    expect(within(category('Tag: team')).getAllByRole('button').map((b) => b.textContent)).toEqual(['Redwood', 'Summit']);
    expect(within(category('Tag: env')).getAllByRole('button').map((b) => b.textContent)).toEqual(['Mock']);
  });

  it('toggling a tag value writes it to the hash and filters the count', () => {
    render(<PoolPage data={pool()} />);
    fireEvent.click(within(category('Tag: team')).getByText('Summit'));
    expect(new URLSearchParams(location.hash.split('?')[1]).get('tag.team')).toBe('Summit');
    expect(screen.getByText('1 / 3')).toBeInTheDocument();

    fireEvent.click(within(category('Tag: team')).getByText('Summit'));
    expect(location.hash).not.toContain('tag.team');
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
  });

  it('restores tag filters from a shared link', () => {
    history.replaceState(null, '', '#?tag.team=Redwood');
    render(<PoolPage data={pool()} />);
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
  });

  it('offers grouping by each tag group', () => {
    render(<PoolPage data={pool()} />);
    const options = [...screen.getByDisplayValue('No grouping').querySelectorAll('option')].map((o) => o.value);
    expect(options).toEqual(expect.arrayContaining(['tag:env', 'tag:team']));
  });

  it('adds nothing when the report has no tags', () => {
    render(<PoolPage data={pool([shortTest('a'), shortTest('b', [])])} />);
    expect(screen.queryByText(/Tag:/)).toBeNull();
    const options = [...screen.getByDisplayValue('No grouping').querySelectorAll('option')].map((o) => o.value);
    expect(options.some((o) => o.startsWith('tag:'))).toBe(false);
  });

  it('shows each test tags on its row', () => {
    const { container } = render(<PoolPage data={pool()} />);
    const row = [...container.querySelectorAll<HTMLAnchorElement>('a')].find((a) => a.textContent?.includes('redwood'))!;
    expect([...row.querySelectorAll('.tag-chip')].map((c) => c.textContent)).toEqual(['teamRedwood', 'envMock']);
  });

  it('notes on group headers that a test can be listed under several values', () => {
    history.replaceState(null, '', '#?group=tag:team');
    render(<PoolPage data={pool()} />);
    expect(screen.getAllByText(/listed under each/)).toHaveLength(3);
  });
});
