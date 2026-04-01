import "./UtilizationLivePanel.css";

export default function UtilizationLivePanel({ latestByResource }) {
  const resources = Object.values(latestByResource);

  if (!resources.length) {
    return <p className="message message--info">No utilization data</p>;
  }

  return (
    <table className="table utilization-table">
      <thead>
        <tr>
          <th>Resource</th>
          <th>Status</th>
          <th>Booking</th>
          <th>Captured At</th>
        </tr>
      </thead>
      <tbody>
        {resources.map((log) => (
          <tr key={log.resource?.id || log.id}>
            <td>{log.resource?.resourceName || "Resource"}</td>
            <td>
              <span className={log.usageState === "IN_USE" ? "badge badge--success" : "badge badge--neutral"}>
                {log.usageState}
              </span>
            </td>
            <td>{log.booking?.id ? `Booking #${log.booking.id}` : "-"}</td>
            <td>{log.capturedAt}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
