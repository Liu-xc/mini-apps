extends SceneTree
## Solver smoke check only: collision shapes have no visual meshes.

func _initialize() -> void:
	call_deferred("run_check")

func run_check() -> void:
	var world := Node3D.new()
	root.add_child(world)
	var floor_body := StaticBody3D.new()
	var floor_shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(10, 1, 10)
	floor_shape.shape = box
	floor_body.add_child(floor_shape)
	world.add_child(floor_body)
	floor_body.position.y = -0.5
	var bodies: Array[RigidBody3D] = []
	for height in [2.0, 4.0]:
		var body := RigidBody3D.new()
		var collider := CollisionShape3D.new()
		var sphere := SphereShape3D.new()
		sphere.radius = 0.5
		collider.shape = sphere
		body.add_child(collider)
		body.continuous_cd = true
		body.physics_material_override = PhysicsMaterial.new()
		body.physics_material_override.friction = 0.65
		body.physics_material_override.bounce = 0.15
		world.add_child(body)
		body.position.y = height
		bodies.append(body)
	var crossed_floor := false
	for frame in 960:
		await physics_frame
		for body in bodies:
			crossed_floor = crossed_floor or body.position.y < 0.4
	var settled := not crossed_floor
	var positions: Array[Dictionary] = []
	for index in bodies.size():
		var body := bodies[index]
		var expected := 0.5 + index
		settled = settled and abs(body.position.y - expected) < 0.08 and body.linear_velocity.length() < 0.05
		positions.append({"position": str(body.position), "velocity": str(body.linear_velocity), "sleeping": body.sleeping})
	print(JSON.stringify({"physics_engine": ProjectSettings.get_setting("physics/3d/physics_engine"),
		"ticks": 960, "bodies": positions, "crossed_floor": crossed_floor, "passed": settled,
		"scope": "Desktop Jolt gravity/contact/stacking smoke only; not lottery asset, high-speed CCD or Android verification."}, "\t"))
	world.free()
	quit(0 if settled else 1)
