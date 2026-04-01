import { useMemo, useState } from "react";
import allocationApi from "../services/allocationApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";

const DAY_TABS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
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

function timeSlotIndex(time) {
  return TIME_SLOTS.indexOf(time);
}

function timeSlotParts(time) {
  const [start, end] = (time || "").split(" - ").map((value) => value.trim());
  return { start, end };
}

function getRoomLabel(allocation) {
  if (allocation.allocationType === "ACTIVITY") {
    return "No room needed";
  }
  return allocation.room?.resourceCode || "Room pending";
}

function displayType(type) {
  if (type === "LAB") {
    return "Lab";
  }
  if (type === "ACTIVITY") {
    return "Activity";
  }
  return "Lecture";
}

function canMerge(previousBlock, allocation) {
  if (!previousBlock) {
    return false;
  }

  return (
    previousBlock.section === allocation.section &&
    previousBlock.subject === allocation.subject &&
    previousBlock.typeLabel === displayType(allocation.allocationType) &&
    previousBlock.roomLabel === getRoomLabel(allocation) &&
    previousBlock.lastTimeIndex + 1 === timeSlotIndex(allocation.time)
  );
}

function mergeAllocations(allocations) {
  const sorted = [...allocations].sort((left, right) => timeSlotIndex(left.time) - timeSlotIndex(right.time));
  const merged = [];

  sorted.forEach((allocation) => {
    const slotIndex = timeSlotIndex(allocation.time);
    const previous = merged[merged.length - 1];
    const { start, end } = timeSlotParts(allocation.time);

    if (canMerge(previous, allocation)) {
      previous.periods.push(slotIndex + 1);
      previous.end = end;
      previous.lastTimeIndex = slotIndex;
      return;
    }

    merged.push({
      key: `${allocation.day}-${allocation.section}-${allocation.time}-${allocation.subject}`,
      section: allocation.section,
      subject: allocation.subject,
      facultyName: allocation.facultyName || "TBA",
      roomLabel: getRoomLabel(allocation),
      typeLabel: displayType(allocation.allocationType),
      periods: slotIndex >= 0 ? [slotIndex + 1] : [],
      start,
      end,
      lastTimeIndex: slotIndex,
    });
  });

  return merged.map((entry) => ({
    ...entry,
    periodLabel: entry.periods.length ? `${entry.periods.join("&")} hrs` : "Slot",
    timeLabel: `${entry.start} to ${entry.end}`,
  }));
}

function downloadCsv(filename, rows) {
  const header = "Day,Section,Period,Time,Subject,Room,Type\n";
  const body = rows
    .map((row) =>
      [row.day, row.section, row.periodLabel, row.timeLabel, row.subject, row.roomLabel, row.typeLabel]
        .map((value) => `"${value}"`)
        .join(",")
    )
    .join("\n");
  const blob = new Blob([header + body], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export default function FacultyTimetablePage() {
  const { user } = useAuth();
  const [allocations, setAllocations] = useState([]);
  const [selectedDay, setSelectedDay] = useState("Monday");
  const [error, setError] = useState("");

  const loadTimetable = async () => {
    if (!user?.id) {
      return;
    }
    try {
      const data = await allocationApi.listFacultyAllocations(user.id);
      setAllocations(data);
      setError("");
    } catch (err) {
      setAllocations([]);
      setError(getErrorMessage(err, "Failed to load faculty timetable"));
    }
  };

  usePolling(loadTimetable, 5000);

  const groupedDay = useMemo(() => {
    const dayRows = allocations.filter((allocation) => allocation.day === selectedDay);
    const grouped = new Map();

    dayRows.forEach((allocation) => {
      const key = allocation.section;
      const current = grouped.get(key) || [];
      current.push(allocation);
      grouped.set(key, current);
    });

    return Array.from(grouped.entries())
      .map(([section, rows]) => ({
        section,
        blocks: mergeAllocations(rows),
      }))
      .sort((left, right) => left.section.localeCompare(right.section));
  }, [allocations, selectedDay]);

  const exportRows = useMemo(
    () =>
      DAY_TABS.flatMap((day) =>
        allocations
          .filter((allocation) => allocation.day === day)
          .reduce((accumulator, allocation) => {
            const key = `${day}-${allocation.section}`;
            const existing = accumulator.find((item) => item.key === key);
            if (existing) {
              existing.rows.push(allocation);
            } else {
              accumulator.push({ key, section: allocation.section, day, rows: [allocation] });
            }
            return accumulator;
          }, [])
          .flatMap((group) =>
            mergeAllocations(group.rows).map((block) => ({
              day,
              section: group.section,
              periodLabel: block.periodLabel,
              timeLabel: block.timeLabel,
              subject: block.subject,
              roomLabel: block.roomLabel,
              typeLabel: block.typeLabel,
            }))
          )
      ),
    [allocations]
  );

  return (
    <div className="page faculty-page">
      <div className="faculty-page__header">
        <div>
          <h2>My Timetable</h2>
          <p>Only your allocated classes are shown here, merged into continuous periods.</p>
        </div>
        <div className="faculty-page__actions">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={() => downloadCsv("my-timetable.csv", exportRows)}
          >
            Export CSV
          </button>
          <button type="button" className="btn btn--primary" onClick={() => window.print()}>
            Save PDF
          </button>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}

      <div className="day-tab-list">
        {DAY_TABS.map((day) => (
          <button
            key={day}
            type="button"
            className={`day-tab ${selectedDay === day ? "day-tab--active" : ""}`}
            onClick={() => setSelectedDay(day)}
          >
            {day}
          </button>
        ))}
      </div>

      <div className="faculty-card-list">
        {groupedDay.length ? (
          groupedDay.map((sectionGroup) => (
            <div className="card faculty-card" key={`${selectedDay}-${sectionGroup.section}`}>
              <div className="faculty-card__header">
                <h3>{sectionGroup.section}</h3>
                <span>{selectedDay}</span>
              </div>
              <div className="merged-slot-list">
                {sectionGroup.blocks.map((block) => (
                  <div className="merged-slot-card" key={block.key}>
                    <div className="merged-slot-top">
                      <span className="merged-slot-period">{block.periodLabel}</span>
                      <span className="merged-slot-time">{block.timeLabel}</span>
                    </div>
                    <div className="merged-slot-title">{block.subject}</div>
                    <div className="merged-slot-meta">{block.facultyName}</div>
                    <div className="merged-slot-bottom">
                      <span className="badge badge--primary">{block.typeLabel}</span>
                      <span className="badge badge--success">{block.roomLabel}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        ) : (
          <p className="message message--info">No classes found for {selectedDay}.</p>
        )}
      </div>
    </div>
  );
}
