import axios from "axios";


export function getFileRequest(
  path: string,
  handleResponse: (response: any) => void,
  handleError: (error: any) => void
) {
  axios.get(path)
    .then((response) => {
      const responseData = response.data
      handleResponse(responseData)
    })
    .catch(error => {
      handleError(error)
    })
}

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
      alert("Got error from post request: " + path)
    })
}

