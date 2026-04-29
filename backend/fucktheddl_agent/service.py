from fucktheddl_agent.schemas import AgentRequest, AgentResponse
from fucktheddl_agent.workflow import build_agent_graph, to_response


class AgentService:
    def __init__(self) -> None:
        self._graph = build_agent_graph()

    def propose(self, request: AgentRequest) -> AgentResponse:
        state = self._graph.invoke(
            {
                "text": request.text,
                "session_id": request.session_id,
                "timezone": request.timezone,
            },
            config={"configurable": {"thread_id": request.session_id}},
        )
        return to_response(state)

