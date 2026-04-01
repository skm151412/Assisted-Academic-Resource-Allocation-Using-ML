import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import App from "./App";
import "./styles/reset.css";
import "./styles/theme.css";
import "./styles/layout.css";

const container = document.getElementById("root");
const root = createRoot(container);

root.render(
  <StrictMode>
    <App />
  </StrictMode>
);
