import { useEffect, useMemo, useState } from "react";
import analyticsApi from "../services/analyticsApi";
import FiltersPanel from "../components/FiltersPanel";
import UnderUtilizationTable from "../components/UnderUtilizationTable";
import "./AnalyticsPage.css";

export default function AnalyticsPage() {
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState({
    startDate: "",
    endDate: "",
    resourceType: "",
  });

  useEffect(() => {
    const fetchLogs = async () => {
      try {
        const data = await analyticsApi.listUsageLogs();
        setLogs(data);
        setError("");
      } catch (err) {
        setError("Failed to load analytics");
      }
    };

    fetchLogs();
  }, []);

  const resourceTypes = useMemo(() => {
    const types = new Set();
    logs.forEach((log) => {
      if (log.resource?.resourceType) {
        types.add(log.resource.resourceType);
      }
    });
    return Array.from(types);
  }, [logs]);

  const filteredLogs = useMemo(() => {
    return logs.filter((log) => {
      const capturedAt = log.capturedAt ? new Date(log.capturedAt) : null;
      if (!capturedAt) {
        return false;
      }
      if (filters.startDate) {
        const start = new Date(`${filters.startDate}T00:00:00`);
        if (capturedAt < start) {
          return false;
        }
      }
      if (filters.endDate) {
        const end = new Date(`${filters.endDate}T23:59:59`);
        if (capturedAt > end) {
          return false;
        }
      }
      if (filters.resourceType && log.resource?.resourceType !== filters.resourceType) {
        return false;
      }
      return true;
    });
  }, [logs, filters]);

  const reportRows = useMemo(() => {
    const summary = new Map();

    filteredLogs.forEach((log) => {
      const resourceId = log.resource?.id;
      if (!resourceId) {
        return;
      }
      if (!summary.has(resourceId)) {
        summary.set(resourceId, {
          resourceId,
          resourceName: log.resource?.resourceName || "Resource",
          resourceType: log.resource?.resourceType || "",
          total: 0,
          inUse: 0,
        });
      }
      const entry = summary.get(resourceId);
      entry.total += 1;
      if (log.usageState === "IN_USE") {
        entry.inUse += 1;
      }
    });

    return Array.from(summary.values()).map((entry) => {
      const utilization = entry.total ? (entry.inUse / entry.total) * 100 : 0;
      const idle = entry.total - entry.inUse;
      return {
        ...entry,
        utilization: utilization.toFixed(1),
        idle,
      };
    });
  }, [filteredLogs]);

  return (
    <div className="page admin-page admin-page--analytics analytics-summary">
      <h2>Under-Utilization Analytics</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      <div className="card">
        <FiltersPanel
          filters={filters}
          resourceTypes={resourceTypes}
          onChange={setFilters}
        />
      </div>
      <div className="table-wrap">
        <UnderUtilizationTable rows={reportRows} />
      </div>
    </div>
  );
}
