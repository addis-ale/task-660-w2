let authToken = localStorage.getItem("hm_token") || "";

export function setToken(token) {
  authToken = token || "";
  if (authToken) {
    localStorage.setItem("hm_token", authToken);
  } else {
    localStorage.removeItem("hm_token");
  }
}

export function getToken() {
  return authToken;
}
