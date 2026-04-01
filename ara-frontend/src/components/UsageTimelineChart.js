import "./UsageTimelineChart.css";

export default function UsageTimelineChart({ logs }) {
  if (!logs.length) {
    return <p className="message message--info">No usage timeline available</p>;
  }

  return (
    <ul className="timeline-list">
      {logs.map((log) => (
        <li key={log.id} className="timeline-item">
          <strong>{log.resource?.resourceName || "Resource"}</strong> | {log.usageState} | {log.capturedAt}
        </li>
      ))}
    </ul>
  );
}
