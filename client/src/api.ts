import axios from "axios";


export function postRequest<REQUEST, RESPONSE>(
  path: string,
  requestData: REQUEST,
  handleResponse: (response: RESPONSE) => void
) {
  // console.log("post request:", path, requestData)
  axios.post(path, requestData)
    .then(function (response) {
      const responseData: RESPONSE = response.data
      // console.log("post response:", path, responseData)
      handleResponse(responseData)
    })
    .catch(error => {
      console.error("Got error from post request", path, error)
    })
}

const apiPrefix = "/api/"

export function apiRequest<REQUEST, RESPONSE>(
  path: string,
  requestData: REQUEST,
  handleResponse: (response: RESPONSE) => void
) {
  const fullPath = apiPrefix + path
  postRequest<REQUEST, RESPONSE>(fullPath, requestData, handleResponse)
}
