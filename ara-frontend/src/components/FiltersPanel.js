import "./FiltersPanel.css";

export default function FiltersPanel({ filters, resourceTypes, onChange }) {
  const handleChange = (field) => (event) => {
    onChange({
      ...filters,
      [field]: event.target.value,
    });
  };

  return (
    <div className="filters-panel">
      <div className="form-row">
        <label htmlFor="startDate">Start Date</label>
        <input
          id="startDate"
          type="date"
          className="input"
          value={filters.startDate}
          onChange={handleChange("startDate")}
        />
      </div>
      <div className="form-row">
        <label htmlFor="endDate">End Date</label>
        <input
          id="endDate"
          type="date"
          className="input"
          value={filters.endDate}
          onChange={handleChange("endDate")}
        />
      </div>
      <div className="form-row">
        <label htmlFor="resourceType">Resource Type</label>
        <select
          id="resourceType"
          className="select"
          value={filters.resourceType}
          onChange={handleChange("resourceType")}
        >
          <option value="">All</option>
          {resourceTypes.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
