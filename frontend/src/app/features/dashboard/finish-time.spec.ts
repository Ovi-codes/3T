import { formatFinishTime, parseFinishTime } from './finish-time';

describe('parseFinishTime', () => {
  it('reads minutes:seconds', () => {
    expect(parseFinishTime('24:31')).toBe(1471);
    expect(parseFinishTime('9:05')).toBe(545);
  });

  it('reads hours:minutes:seconds', () => {
    expect(parseFinishTime('1:00:00')).toBe(3600);
    expect(parseFinishTime('0:24:31')).toBe(1471);
  });

  it('ignores surrounding spaces', () => {
    expect(parseFinishTime('  24:31 ')).toBe(1471);
  });

  it('rejects anything that is not a clock time', () => {
    for (const text of ['', '24', '24:6', '24:61', '1:60:00', 'abc', '24:31:', '-24:31', '24.31']) {
      expect(parseFinishTime(text), text).toBeNull();
    }
  });
});

describe('formatFinishTime', () => {
  it('writes minutes:seconds under an hour', () => {
    expect(formatFinishTime(1471)).toBe('24:31');
    expect(formatFinishTime(605)).toBe('10:05');
  });

  it('writes hours:minutes:seconds from an hour up', () => {
    expect(formatFinishTime(3600)).toBe('1:00:00');
  });
});
