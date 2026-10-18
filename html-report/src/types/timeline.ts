// Mirror of `TimelineExecutionResult` (Kotlin, report/execution-timeline).
// Field names match the emitted JSON verbatim.

export type MetricType =
  | 'PASSED'
  | 'FAILURE'
  | 'IGNORED'
  | 'INCOMPLETE'
  | 'ASSUMPTION_FAILURE'
  | 'DEVICE_PROVIDER_INIT'
  | 'DEVICE_PREPARE'
  | 'TEST_PARSING'
  | 'DEVICE_PHASE';

export interface TimelineData {
  testName: string;
  metricType: MetricType;
  startDate: number;
  endDate: number;
  expectedValue: number;
  variance: number;
  // Populated when the timeline reporter is upgraded to schema >= 2:
  batchId?: string;
  attemptIndex?: number;
  poolId?: string;
  testFilename?: string;
  deviceSerial?: string;
  /** Test parsing bars: the attempt's device log page, relative to the report root. */
  logHref?: string;
  /** Test parsing and device phase bars: the step failed. */
  failed?: boolean;
  /** Device phase bars: the DevicePhase name; `testName` holds the step's label. */
  phase?: DevicePhase;
  /** Device phase bars: repeats around every batch (pm clear, instrumentation start, ...). */
  betweenTests?: boolean;
}

export type DevicePhase =
  | 'INSTALL'
  | 'DEVICE_RESET'
  | 'CLEAR_DATA'
  | 'BEFORE_RUN'
  | 'INSTRUMENTATION_START'
  | 'AFTER_RUN';

export interface TimelineDevice {
  serial?: string;
  modelName?: string;
  manufacturer?: string;
  osVersion?: string;
  osMajor?: number | null;
}

export interface TimelineMeasure {
  measure: string; // device serial
  stats: {
    idleTimeMillis: number;
    averageTestExecutionTimeMillis: number;
  };
  data: TimelineData[];
  /** Optional in schema 2+; the chart uses this for tooltip enrichment. */
  device?: TimelineDevice;
}

export interface TimelineExecutionResult {
  reportSchemaVersion?: number;
  passedTests: number;
  failedTests: number;
  ignoredTests: number;
  executionStats: {
    idleTimeMillis: number;
    averageTestExecutionTimeMillis: number;
  };
  measures: TimelineMeasure[];
}
