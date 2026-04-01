import "./UnderUtilizationTable.css";

export default function UnderUtilizationTable({ rows }) {
  if (!rows.length) {
    return <p className="message message--info">No analytics data</p>;
  }

  return (
    <table className="table analytics-table">
      <thead>
        <tr>
          <th>Resource</th>
          <th>Type</th>
          <th>Utilization %</th>
          <th>Idle Count</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.resourceId}>
            <td>{row.resourceName}</td>
            <td>{row.resourceType}</td>
            <td>{row.utilization}</td>
            <td>{row.idle}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
