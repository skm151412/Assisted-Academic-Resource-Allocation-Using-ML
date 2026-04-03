import apiClient from "./apiClient";

export async function sendChatMessage(message) {
  const response = await apiClient.post("/chat", { message }, { timeout: 300000 });
  return response?.data?.reply || "I could not generate a response right now. Please try again.";
}