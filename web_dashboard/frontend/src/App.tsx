import React, { useState, useEffect } from "react";
import { Line } from "react-chartjs-2";

const Dashboard = () => {
    const [poseData, setPoseData] = useState([]);

    useEffect(() => {
        const ws = new WebSocket("ws://localhost:3000");
        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            setPoseData(data);
        };
        return () => ws.close();
    }, []);

    const chartData = {
        labels: poseData.map((_, index) => index),
        datasets: [
            {
                label: "X Position",
                data: poseData.map(p => p[0]),
                borderColor: "red",
                fill: false
            },
            {
                label: "Y Position",
                data: poseData.map(p => p[1]),
                borderColor: "blue",
                fill: false
            },
            {
                label: "Z Position",
                data: poseData.map(p => p[2]),
                borderColor: "green",
                fill: false
            }
        ]
    };

    return (
        <div>
            <h1>FBT Pose Data</h1>
            <Line data={chartData} />
        </div>
    );
};

export default Dashboard;