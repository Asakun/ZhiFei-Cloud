export interface DroneOpen {
    bid: string,
    output: {
        progress: {
            current_step: number,
            percent: number,
            step_key: string,
            step_result: number,
            total_steps: number,
        },
        status: string,
    },
    result: number,
    sn: string
}

export interface PSDKFloatingWindowText {
    text: string,
    sn: string,
}
