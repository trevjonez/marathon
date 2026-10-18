import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '../theme/ThemeProvider';
import { TestPage } from './TestPage';
import type { HtmlFullTest } from '../types/report';
import { device } from './PoolPage/testFixtures';

beforeAll(() => {
  window.matchMedia ??= ((query: string) => ({
    matches: false,
    media: query,
    addEventListener() {},
    removeEventListener() {},
  })) as unknown as typeof window.matchMedia;
});

function fullTest(tags?: HtmlFullTest['tags']): HtmlFullTest {
  return {
    pool_id: 'omni',
    package_name: 'com.example',
    class_name: 'Suite',
    name: 'tagged',
    id: 'com.example.Suite.tagged',
    filename: 'com.example.Suite.tagged.html',
    duration_millis: 1,
    status: 'passed',
    stacktrace: null,
    start_time_ms: 0,
    end_time_ms: 1,
    batch_id: 'b',
    device,
    device_id: device.serial,
    diagnostic_video: false,
    diagnostic_screenshots: false,
    screenshot: '',
    videos: [],
    log_file: '',
    attempts: [],
    attempt_count: 1,
    is_flaky: false,
    distinct_devices: [device],
    ...(tags ? { tags } : {}),
  };
}

const renderPage = (data: HtmlFullTest) => render(<ThemeProvider><TestPage data={data} /></ThemeProvider>);

describe('TestPage tags', () => {
  it('shows each tag with its attributes inline and in the tooltip', () => {
    renderPage(
      fullTest([
        { group: 'team', value: 'Redwood', attributes: { description: 'Claims EOB', ticket: 'RW-12' } },
        { group: 'env', value: 'Mock', attributes: {} },
      ]),
    );
    const chips = [...screen.getByLabelText('Tags').querySelectorAll('.tag-chip')];
    expect(chips.map((c) => c.textContent)).toEqual(['teamRedwood· Claims EOB· RW-12', 'envMock']);
    expect(chips[0]!.getAttribute('title')).toBe('description: Claims EOB\nticket: RW-12');
    expect(chips[1]!.hasAttribute('title')).toBe(false);
  });

  it('has no tag row without tags, including reports from before tags shipped', () => {
    renderPage(fullTest([]));
    expect(screen.queryByLabelText('Tags')).toBeNull();
    renderPage(fullTest());
    expect(screen.queryByLabelText('Tags')).toBeNull();
  });
});
