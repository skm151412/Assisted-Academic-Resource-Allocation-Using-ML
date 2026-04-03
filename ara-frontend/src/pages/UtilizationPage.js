import { useEffect, useMemo, useState } from "react";
import utilizationApi from "../services/utilizationApi";
import usePolling from "../hooks/usePolling";
import UtilizationLivePanel from "../components/UtilizationLivePanel";
import UsageTimelineChart from "../components/UsageTimelineChart";
import "./UtilizationPage.css";

export default function UtilizationPage() {
  const [usageLogs, setUsageLogs] = useState([]);
  const [error, setError] = useState("");

  const fetchUsageLogs = async () => {
    try {
      const data = await utilizationApi.listUsageLogs();
      setUsageLogs(data);
      setError("");
    } catch (err) {
      setError("Failed to load utilization");
    }
  };

  useEffect(() => {
    fetchUsageLogs();
  }, []);

  usePolling(fetchUsageLogs, 30000);

  const latestByResource = useMemo(() => {
    const latestMap = {};
    for (const log of usageLogs) {
      const resourceId = log.resource?.id;
      if (!resourceId) {
        continue;
      }
      const existing = latestMap[resourceId];
      if (!existing) {
        latestMap[resourceId] = log;
        continue;
      }
      if (new Date(log.capturedAt) > new Date(existing.capturedAt)) {
        latestMap[resourceId] = log;
      }
    }
    return latestMap;
  }, [usageLogs]);

  return (
    <div className="page admin-page admin-page--utilization utilization-section">
      <h2>Utilization</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      <div className="table-wrap">
        <UtilizationLivePanel latestByResource={latestByResource} />
      </div>
      <div className="card">
        <h3>Usage Timeline</h3>
        <UsageTimelineChart logs={usageLogs} />
      </div>
    </div>
  );
}
