from urllib import response
from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
from api_data.request import AgentSyncRequest
from api_data.response import Action, AgentSyncOutput, AgentSyncResponse, ScriptToRun
from pydantic import ValidationError
from fastapi.encoders import jsonable_encoder

import logging

app = FastAPI()

logger = logging.getLogger(__name__)

@app.exception_handler(ValidationError)
async def exception_handler(request, exc: ValidationError):
    print(request.json())
    raise exc


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(exc)
    
    return JSONResponse(
        content=jsonable_encoder({"validation_error": exc.errors()}), 
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY
    )


@app.get("/")
async def root():
    return {"message": "Botfarm Python Agent Server"}

@app.post("/api/sync")
async def sync(request: AgentSyncRequest):
    if request.input.agentType == "script":
        return AgentSyncResponse(
            outputs=[
                AgentSyncOutput(
                    scriptToRun=ScriptToRun(
                        scriptId="abc123",
                        script="""
                        speak("Hello from Python via JavaScript!")
                        """
                    )
                )
            ]
        )
    else:
        return AgentSyncResponse(
            outputs=[
                AgentSyncOutput(
                    actions=[
                        Action(
                            actionUniqueId="abc123",
                            speak="Hello from Python!"
                        )
                    ]
                )
            ]
        )