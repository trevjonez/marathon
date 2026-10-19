import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '../../theme/ThemeProvider';
import { LogsPage } from './LogsPage';
import type { HtmlTestLogDetails } from '../../types/report';
import { device } from '../PoolPage/testFixtures';

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

beforeEach(() => {
  history.replaceState(null, '', '#');
  // A capture the browser can't fetch (file://, missing file) falls back to the inlined body
  vi.stubGlobal('fetch', () => Promise.reject(new Error('unavailable')));
});

function details(overrides: Partial<HtmlTestLogDetails> = {}): HtmlTestLogDetails {
  return {
    pool_id: 'omni',
    test_id: 'com.example.Suite.t1',
    display_name: 't1',
    attempts: [
      {
        attempt_index: 0,
        final: true,
        status: 'failed',
        device_id: device.serial,
        device,
        log_path: '../../test-parsing/x.log',
        log_body: '10-18 02:57:54.000 1-1/app E/AndroidRuntime: FATAL EXCEPTION: main\n',
      },
    ],
    ...overrides,
  } as HtmlTestLogDetails;
}

const crumbs = () => [...document.querySelectorAll('a')].map((a) => [a.textContent, a.getAttribute('href')]);
const renderPage = (data: HtmlTestLogDetails) => render(<ThemeProvider><LogsPage data={data} /></ThemeProvider>);

describe('LogsPage for a test parsing log', () => {
  it('breadcrumbs lead back to the index from html/test-parsing/', () => {
    renderPage(details({ kind: 'test-parsing', pool_id: '', test_id: 'test-parsing', display_name: 'Test parsing on emulator-5554' }));
    expect(crumbs()).toContainEqual(['Pools list', '../index.html']);
    expect(crumbs().some(([text]) => text?.startsWith('Pool '))).toBe(false);
    expect(screen.getAllByText(/Test parsing on emulator-5554/).length).toBeGreaterThan(0);
  });

  it('shows the inlined capture, parsed as logcat', async () => {
    renderPage(details({ kind: 'test-parsing' }));
    expect(await screen.findByText(/FATAL EXCEPTION: main/)).toBeInTheDocument();
    expect(screen.getByText(/Parsed with/).textContent).toMatch(/logcat/i);
  });

  it('an install log gets the same device-log breadcrumbs', () => {
    renderPage(details({ kind: 'install', pool_id: '', test_id: 'install', display_name: 'Installs on emulator-5554' }));
    expect(crumbs()).toContainEqual(['Pools list', '../index.html']);
    expect(crumbs().some(([text]) => text?.startsWith('Pool '))).toBe(false);
  });

  it('keeps the pool and test breadcrumbs for a test log', () => {
    renderPage(details());
    expect(crumbs()).toContainEqual(['Pools list', '../../../../index.html']);
    expect(crumbs().some(([text]) => text === 'Pool omni')).toBe(true);
  });
});
