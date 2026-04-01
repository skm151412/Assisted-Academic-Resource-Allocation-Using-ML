import { useEffect, useMemo, useState } from "react";
import allocationApi from "../services/allocationApi";
import usePolling from "../hooks/usePolling";
import { getErrorMessage } from "../services/apiClient";
import "./AutoAllocationPage.css";

const DEFAULT_YEAR_CONFIGS = [
  {
    name: "II Year",
    sections: "E1, E2, E3, A1, A2, A3, A4, A5, A6",
    excludedDays: "Wednesday",
  },
  {
    name: "I Year",
    sections: "",
    excludedDays: "",
  },
  {
    name: "III Year",
    sections: "",
    excludedDays: "",
  },
];

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const TIME_SLOTS = [
  "08:15 AM - 09:05 AM",
  "09:05 AM - 09:55 AM",
  "10:10 AM - 11:00 AM",
  "11:00 AM - 11:50 AM",
  "11:50 AM - 12:45 PM",
  "01:30 PM - 02:20 PM",
  "02:20 PM - 03:10 PM",
  "03:10 PM - 04:00 PM",
];

const CSV_TEMPLATE = [
  "yearGroup,section,day,time,subject,facultyName,allocationType",
  "III Year,C1,Monday,08:15 AM - 09:05 AM,Cloud Computing,Dr. Sample Faculty,LECTURE",
  "III Year,C1,Monday,09:05 AM - 09:55 AM,Cloud Computing (T),Dr. Sample Faculty,TUTORIAL",
  "III Year,C1,Tuesday,10:10 AM - 11:00 AM,Cloud Computing (P),Dr. Sample Faculty,LAB",
  "III Year,C1,Tuesday,11:00 AM - 11:50 AM,Cloud Computing (P),Dr. Sample Faculty,LAB",
].join("\n");

const TXT_TEMPLATE = [
  "III Year|C1|Monday|08:15 AM - 09:05 AM|Cloud Computing|Dr. Sample Faculty|LECTURE",
  "III Year|C1|Monday|09:05 AM - 09:55 AM|Cloud Computing (T)|Dr. Sample Faculty|TUTORIAL",
  "III Year|C1|Tuesday|10:10 AM - 11:00 AM|Cloud Computing (P)|Dr. Sample Faculty|LAB",
  "III Year|C1|Tuesday|11:00 AM - 11:50 AM|Cloud Computing (P)|Dr. Sample Faculty|LAB",
].join("\n");

function splitList(value) {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function isHeaderLine(line) {
  const normalized = line.toLowerCase().replace(/\s+/g, "");
  return normalized.includes("yeargroup") || normalized.includes("year,section,day,time,subject");
}

function parseTimetableText(text) {
  if (!text.trim()) {
    return { entries: [], errors: [] };
  }

  const entries = [];
  const errors = [];
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && !isHeaderLine(line));

  lines.forEach((line, index) => {
    const separator = line.includes("|") ? "|" : ",";
    const parts = line.split(separator).map((part) => part.trim().replace(/^"|"$/g, ""));

    if (parts.length !== 7) {
      errors.push(`Line ${index + 1} must contain 7 values`);
      return;
    }

    entries.push({
      yearGroup: parts[0],
      section: parts[1],
      day: parts[2],
      time: parts[3],
      subject: parts[4],
      facultyName: parts[5],
      allocationType: parts[6],
    });
  });

  const deduped = [];
  const seen = new Set();

  entries.forEach((entry) => {
    const key = [entry.yearGroup, entry.section, entry.day, entry.time, entry.subject, entry.facultyName, entry.allocationType]
      .map((value) => (value || "").trim().toUpperCase())
      .join("|");

    if (seen.has(key)) {
      return;
    }

    seen.add(key);
    deduped.push(entry);
  });

  return { entries: deduped, errors };
}

function downloadTemplateFile(filename, content, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function sectionLabel(allocation) {
  return `${allocation.yearGroup || ""} - ${allocation.section || ""}`.trim();
}

export default function AutoAllocationPage() {
  const [allocations, setAllocations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [yearConfigs, setYearConfigs] = useState(DEFAULT_YEAR_CONFIGS);
  const [scheduleText, setScheduleText] = useState("");
  const [selectedSection, setSelectedSection] = useState("");

  const parsedInput = useMemo(() => parseTimetableText(scheduleText), [scheduleText]);

  const sectionOptions = useMemo(() => {
    const seen = new Map();
    allocations.forEach((allocation) => {
      const key = `${allocation.yearGroup}__${allocation.section}`;
      if (!seen.has(key)) {
        seen.set(key, {
          key,
          value: key,
          label: sectionLabel(allocation),
          yearGroup: allocation.yearGroup,
          section: allocation.section,
        });
      }
    });

    return [...seen.values()].sort((left, right) => {
      const yearCompare = (left.yearGroup || "").localeCompare(right.yearGroup || "");
      if (yearCompare !== 0) {
        return yearCompare;
      }
      return (left.section || "").localeCompare(right.section || "");
    });
  }, [allocations]);

  useEffect(() => {
    if (!sectionOptions.length) {
      setSelectedSection("");
      return;
    }

    const exists = sectionOptions.some((option) => option.value === selectedSection);
    if (!exists) {
      setSelectedSection(sectionOptions[0].value);
    }
  }, [sectionOptions, selectedSection]);

  const grid = useMemo(() => {
    const matrix = {};
    DAYS.forEach((day) => {
      matrix[day] = {};
      TIME_SLOTS.forEach((slot) => {
        matrix[day][slot] = null;
      });
    });

    if (!selectedSection) {
      return matrix;
    }

    const [yearGroup, section] = selectedSection.split("__");
    allocations
      .filter((allocation) => allocation.yearGroup === yearGroup && allocation.section === section)
      .forEach((allocation) => {
        if (!matrix[allocation.day] || !Object.prototype.hasOwnProperty.call(matrix[allocation.day], allocation.time)) {
          return;
        }
        if (!matrix[allocation.day][allocation.time]) {
          matrix[allocation.day][allocation.time] = allocation;
        }
      });

    return matrix;
  }, [allocations, selectedSection]);

  const loadAllocations = async () => {
    try {
      const data = await allocationApi.listAllocations();
      setAllocations(data);
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load allocations"));
      setAllocations([]);
    }
  };

  usePolling(loadAllocations, 5000);

  const handleConfigChange = (index, field, value) => {
    setYearConfigs((current) =>
      current.map((config, configIndex) =>
        configIndex === index ? { ...config, [field]: value } : config
      )
    );
  };

  const handleFileImport = async (event) => {
    const files = Array.from(event.target.files || []);
    if (!files.length) {
      return;
    }

    const importedTexts = await Promise.all(files.map((file) => file.text()));
    const mergedText = importedTexts
      .map((text) => text.trim())
      .filter(Boolean)
      .join("\n");

    setScheduleText(mergedText);

    setMessage(files.length === 1 ? `${files[0].name} added.` : `${files.length} files added.`);
    setError("");
    event.target.value = "";
  };

  const handleGenerate = async () => {
    if (parsedInput.errors.length > 0) {
      setError(parsedInput.errors[0]);
      setMessage("");
      return;
    }

    setLoading(true);
    try {
      const payload = {
        yearConfigs: yearConfigs.map((config) => ({
          name: config.name,
          sections: splitList(config.sections),
          excludedDays: splitList(config.excludedDays),
        })),
        entries: parsedInput.entries,
      };

      const data = await allocationApi.generateAllocations(payload);
      setAllocations(data);
      setError("");
      setMessage(`Generated ${data.length} rows.`);
    } catch (err) {
      setError(getErrorMessage(err, "Failed to generate allocations"));
      setMessage("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page allocation-page">
      <div className="header-actions">
        <div>
          <h2>Auto Allocation</h2>
          <p className="allocation-subtitle">Generate and review one clean timetable grid per section.</p>
        </div>
        <div className="allocation-actions">
          <button className="btn btn--primary" onClick={handleGenerate} disabled={loading}>
            {loading ? "Generating..." : "Generate Allocation"}
          </button>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--info allocation-info">{message}</p> : null}

      <div className="allocation-builder-grid">
        <section className="card allocation-panel">
          <div className="panel-header">
            <div>
              <h3>Input Data</h3>
              <p>Upload CSV or TXT timetable rows, then generate.</p>
            </div>
          </div>

          <div className="template-actions">
            <label className="btn btn--ghost allocation-upload">
              Upload CSV / TXT
              <input type="file" accept=".csv,.txt" multiple onChange={handleFileImport} hidden />
            </label>
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() =>
                downloadTemplateFile("auto-allocation-template.csv", CSV_TEMPLATE, "text/csv;charset=utf-8")
              }
            >
              Download CSV Template
            </button>
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() =>
                downloadTemplateFile("auto-allocation-template.txt", TXT_TEMPLATE, "text/plain;charset=utf-8")
              }
            >
              Download TXT Template
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => setScheduleText("")}>
              Clear Rows
            </button>
          </div>

          <div className="year-config-list">
            {yearConfigs.map((config, index) => (
              <div className="year-config-card" key={`${config.name}-${index}`}>
                <div className="form-row">
                  <label>Year Group</label>
                  <input
                    className="input"
                    value={config.name}
                    onChange={(event) => handleConfigChange(index, "name", event.target.value)}
                  />
                </div>
                <div className="form-row">
                  <label>Sections</label>
                  <input
                    className="input"
                    value={config.sections}
                    onChange={(event) => handleConfigChange(index, "sections", event.target.value)}
                    placeholder="A1, A2, E1"
                  />
                </div>
                <div className="form-row">
                  <label>Excluded Days</label>
                  <input
                    className="input"
                    value={config.excludedDays}
                    onChange={(event) => handleConfigChange(index, "excludedDays", event.target.value)}
                    placeholder="Wednesday"
                  />
                </div>
              </div>
            ))}
          </div>

          <div className="form-row">
            <label>CSV / TXT Timetable Rows</label>
            <textarea
              className="allocation-textarea"
              value={scheduleText}
              onChange={(event) => setScheduleText(event.target.value)}
              placeholder={"yearGroup,section,day,time,subject,facultyName,allocationType\nIII Year,C1,Monday,08:15 AM - 09:05 AM,Cloud Computing,Dr. Sample Faculty,LECTURE"}
            />
          </div>
        </section>
      </div>

      <div className="card allocation-results-card">
        <div className="panel-header panel-header--compact">
          <div>
            <h3>Allocated Timetable</h3>
            <p>Day x Slots table. Each cell contains exactly one subject.</p>
          </div>
          <div>
            <select
              className="input"
              value={selectedSection}
              onChange={(event) => setSelectedSection(event.target.value)}
            >
              {sectionOptions.map((option) => (
                <option key={option.key} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {!allocations.length ? (
          <p className="message message--info">No allocations generated yet.</p>
        ) : null}

        {allocations.length && selectedSection ? (
          <div className="table-scroll">
            <table className="table timetable-grid">
              <thead>
                <tr>
                  <th>Day</th>
                  {TIME_SLOTS.map((slot) => (
                    <th key={slot}>{slot}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {DAYS.map((day) => (
                  <tr key={day}>
                    <td className="day-cell">{day}</td>
                    {TIME_SLOTS.map((slot) => {
                      const allocation = grid[day][slot];
                      return (
                        <td key={`${day}-${slot}`}>
                          {allocation ? (
                            <div className="slot-cell">
                              <div className="slot-subject">{allocation.subject}</div>
                              <div className="slot-meta">{allocation.facultyName || "TBA"}</div>
                              <div className="slot-meta">
                                {allocation.allocationType} | {allocation.room?.resourceCode || "N/A"}
                              </div>
                            </div>
                          ) : (
                            <div className="slot-empty">-</div>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </div>
  );
}

