import {postRequest} from "../misc/request";

const apiPrefix = "/api/"

export function apiRequest<REQUEST, RESPONSE>(
  path: string,
  requestData: REQUEST,
  handleResponse: (response: RESPONSE) => void
) {
  const fullPath = apiPrefix + path
  postRequest<REQUEST, RESPONSE>(fullPath, requestData, handleResponse)
}