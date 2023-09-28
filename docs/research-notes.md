Inputs tokens cost 50% of output tokens.
However, prompts usually have 4,000 tokens and only a few hundred output tokens.
If you can use one prompt to generate 15 seconds worth of agent behavior with 500 output tokens, and 3500 input tokens, that would be much cheaper than 3 prompts generating 5 seconds each at 10,500 input tokens and 500 output tokens.
You could have agents generate 30+ seconds worth of behavior in one prompt, then interrupt that behavior if and only if some new world event invalidates that pre-planned behavior (such as receiving a new message). You could continue to execute the old behavior as best as possible while waiting for the new prompt to process newly received information.
