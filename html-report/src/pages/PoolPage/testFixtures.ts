import type { HtmlShortTest, HtmlTag } from '../../types/report';

export const device = {
  serial: 'emulator-5554',
  model_name: 'sdk',
  manufacturer: 'Google',
  os_version: '37',
  os_major: 37,
  network_state: 'CONNECTED',
  features: [],
  is_tablet: false,
  api_level: '37',
};

export function shortTest(name: string, tags?: HtmlTag[]): HtmlShortTest {
  return {
    id: `com.example.Suite.${name}`,
    package_name: 'com.example',
    filename: `com.example.Suite.${name}.html`,
    class_name: 'Suite',
    name,
    duration_millis: 1,
    status: 'passed',
    start_time_ms: 0,
    end_time_ms: 1,
    batch_id: 'b',
    device_id: device.serial,
    device,
    attempt_count: 1,
    is_flaky: false,
    devices: [device.serial],
    os_versions: [device.os_version],
    has_screenshot: false,
    has_video: false,
    ...(tags ? { tags } : {}),
  };
}
